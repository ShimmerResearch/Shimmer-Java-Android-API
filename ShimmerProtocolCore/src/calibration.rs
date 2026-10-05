//! Inertial sensor calibration, as the Java driver computes it, expression for expression so the
//! doubles come out identical: UtilCalibration (cofactor inverse, multiply), CalibArraysKinematic
//! and CalibDetailsKinematic (defaults, the 21-byte record, rounding), UtilShimmer (BigDecimal
//! HALF_UP rounding), and the gyro on-the-fly offset calibration with Apache Commons Math 2.2's
//! mean and standard deviation.

pub type Matrix = [[f64; 3]; 3];

/// UtilCalibration.matrixInverse3x3, term for term.
pub fn inverse3x3(m: &Matrix) -> Matrix {
    let [[a, b, c], [d, e, f], [g, h, i]] = *m;
    let deter = a * e * i + b * f * g + c * d * h - c * e * g - b * d * i - a * f * h;
    [
        [
            (1.0 / deter) * (e * i - f * h),
            (1.0 / deter) * (c * h - b * i),
            (1.0 / deter) * (b * f - c * e),
        ],
        [
            (1.0 / deter) * (f * g - d * i),
            (1.0 / deter) * (a * i - c * g),
            (1.0 / deter) * (c * d - a * f),
        ],
        [
            (1.0 / deter) * (d * h - e * g),
            (1.0 / deter) * (g * b - a * h),
            (1.0 / deter) * (a * e - b * d),
        ],
    ]
}

/// UtilCalibration.matrixMultiplication for 3x3 by 3x3: accumulates from 0.0 in k order.
fn multiply(a: &Matrix, b: &Matrix) -> Matrix {
    let mut r = [[0.0; 3]; 3];
    for i in 0..3 {
        for j in 0..3 {
            for k in 0..3 {
                r[i][j] += a[i][k] * b[k][j];
            }
        }
    }
    r
}

/// UtilShimmer.applyPrecisionCorrection: `new BigDecimal(value).setScale(places, HALF_UP)`.
/// The exact decimal expansion of the double is rounded half up, then parsed back.
pub fn precision_correction(value: f64, places: usize) -> f64 {
    // A double of magnitude 2^-10 or more has at most 62 fractional digits, so 80 is its exact
    // expansion; anything smaller rounds to zero at the precisions used here (2 places at most).
    let exact = format!("{:.80}", value.abs());
    let (int_part, frac) = exact.split_once('.').unwrap();
    let mut digits: Vec<u8> = int_part
        .bytes()
        .chain(frac.bytes().take(places))
        .map(|d| d - b'0')
        .collect();
    let round_up = frac.as_bytes().get(places).is_some_and(|&d| d >= b'5');
    if round_up {
        let mut i = digits.len();
        loop {
            if i == 0 {
                digits.insert(0, 1);
                break;
            }
            i -= 1;
            if digits[i] == 9 {
                digits[i] = 0;
            } else {
                digits[i] += 1;
                break;
            }
        }
    }
    let split = digits.len() - places;
    let mut text: String = if value.is_sign_negative() {
        "-".into()
    } else {
        String::new()
    };
    text.extend(digits[..split].iter().map(|d| (b'0' + d) as char));
    if places > 0 {
        text.push('.');
        text.extend(digits[split..].iter().map(|d| (b'0' + d) as char));
    }
    // BigDecimal has no negative zero: -0.004 rounds to 0.00, which is +0.0.
    text.parse::<f64>().unwrap() + 0.0
}

/// UtilShimmer.nudgeDoubleArray: clamp into range, then round.
fn nudge(value: f64, lo: f64, hi: f64, places: usize) -> f64 {
    precision_correction(value.clamp(lo, hi), places)
}

/// CalibDetailsKinematic.calculatePrecision.
fn precision(scale: f64) -> usize {
    ((scale.log10() + 1.0) as i64 - 1) as usize
}

/// Calibration read sources, lowest priority first (CalibDetails.CALIB_READ_SOURCE).
pub const UNKNOWN: u8 = 0;
pub const INFOMEM: u8 = 3;
pub const RADIO_DUMP: u8 = 4;

/// One inertial sensor at one range: its defaults, and the current values read from the device.
/// Each current array falls back to its default on its own, as in the Java ("valid").
#[derive(Debug, Clone)]
pub struct KinematicCalibration {
    pub default_offset: [f64; 3],
    pub default_sensitivity: Matrix,
    pub default_alignment: Matrix,
    default_matrix: Matrix,
    pub current_offset: Option<[f64; 3]>,
    pub current_sensitivity: Option<Matrix>,
    pub current_alignment: Option<Matrix>,
    current_matrix: Option<Matrix>,
    pub source: u8,
    sensitivity_scale: f64,
}

const ALIGNMENT_SCALE: f64 = 100.0;

/// inverse(alignment) x inverse(sensitivity), as CalibArraysKinematic precomputes it.
fn premultiplied(alignment: &Matrix, sensitivity: &Matrix) -> Matrix {
    multiply(&inverse3x3(alignment), &inverse3x3(sensitivity))
}

impl KinematicCalibration {
    /// The defaults: an offset and a sensitivity per axis, and the alignment matrix. Records read
    /// from the device later divide their sensitivities by `sensitivity_scale`.
    pub fn new(
        offset: [f64; 3],
        sensitivity: [f64; 3],
        alignment: [[i8; 3]; 3],
        sensitivity_scale: f64,
    ) -> Self {
        let [x, y, z] = sensitivity;
        let sens = [[x, 0.0, 0.0], [0.0, y, 0.0], [0.0, 0.0, z]];
        let align = alignment.map(|row| row.map(|v| v as f64));
        KinematicCalibration {
            default_offset: offset,
            default_sensitivity: sens,
            default_alignment: align,
            default_matrix: premultiplied(&align, &sens),
            current_offset: None,
            current_sensitivity: None,
            current_alignment: None,
            current_matrix: None,
            source: UNKNOWN,
            sensitivity_scale,
        }
    }

    /// CalibDetailsKinematic.parseCalParamByteArray: a 21-byte record, offsets and sensitivities
    /// as big-endian i16, then the alignment matrix as nine i8 / 100.
    pub fn parse(&mut self, record: &[u8], source: u8) {
        if source < self.source
            || record.iter().all(|&b| b == 0xFF)
            || record.iter().all(|&b| b == 0)
        {
            return;
        }
        self.source = source;
        let i16be = |i: usize| i16::from_be_bytes([record[i], record[i + 1]]) as f64;
        let f: Vec<f64> = (0..6)
            .map(|k| i16be(2 * k))
            .chain((12..21).map(|i| record[i] as i8 as f64))
            .collect();
        let am: Vec<f64> = (0..9).map(|i| f[6 + i] / ALIGNMENT_SCALE).collect();
        let alignment = [
            [am[0], am[1], am[2]],
            [am[3], am[4], am[5]],
            [am[6], am[7], am[8]],
        ];
        let mut sensitivity = [[f[3], 0.0, 0.0], [0.0, f[4], 0.0], [0.0, 0.0, f[5]]];
        for (i, row) in sensitivity.iter_mut().enumerate() {
            row[i] /= self.sensitivity_scale;
        }
        let offset = [f[0], f[1], f[2]];

        let bounds = |scale: f64| {
            let places = precision(scale);
            (
                precision_correction(-32768.0 / scale, places),
                precision_correction(32767.0 / scale, places),
                places,
            )
        };
        let (a_lo, a_hi) = (
            precision_correction(-128.0 / 100.0, 2),
            precision_correction(127.0 / 100.0, 2),
        );
        let (s_lo, s_hi, s_places) = bounds(self.sensitivity_scale);
        let (o_lo, o_hi, o_places) = bounds(1.0);
        let alignment = alignment.map(|row| row.map(|v| nudge(v, a_lo, a_hi, 2)));
        let sensitivity = sensitivity.map(|row| row.map(|v| nudge(v, s_lo, s_hi, s_places)));
        self.current_alignment = Some(alignment);
        self.current_sensitivity = Some(sensitivity);
        self.current_offset = Some(offset.map(|v| nudge(v, o_lo, o_hi, o_places)));
        self.current_matrix = Some(premultiplied(&alignment, &sensitivity));
    }

    /// CalibArraysKinematic.updateOffsetVector: replaces the offset as is, not rounded.
    pub fn update_offset(&mut self, offset: [f64; 3]) {
        self.current_offset = Some(offset);
    }

    /// UtilCalibration.calibrateInertialSensorData: inv(R) inv(K) (U - b).
    pub fn apply(&self, u: [f64; 3]) -> [f64; 3] {
        let b = self.current_offset.unwrap_or(self.default_offset);
        let m = self.current_matrix.unwrap_or(self.default_matrix);
        let d = [u[0] - b[0], u[1] - b[1], u[2] - b[2]];
        let mut r = [0.0; 3];
        for i in 0..3 {
            for k in 0..3 {
                r[i] += m[i][k] * d[k];
            }
        }
        r
    }
}

/// Apache Commons Math 2.2 Mean.evaluate: the sum's mean, then a correction pass.
fn mean<'a>(values: impl Iterator<Item = &'a f64> + Clone) -> f64 {
    let n = values.clone().count() as f64;
    let mut total = 0.0;
    for v in values.clone() {
        total += v;
    }
    let xbar = total / n;
    let mut correction = 0.0;
    for v in values {
        correction += v - xbar;
    }
    xbar + (correction / n)
}

/// Commons Math 2.2 DescriptiveStatistics.getStandardDeviation (bias-corrected Variance).
fn standard_deviation<'a>(values: impl Iterator<Item = &'a f64> + Clone) -> f64 {
    let n = values.clone().count();
    if n == 1 {
        return 0.0;
    }
    let m = mean(values.clone());
    let (mut accum, mut accum2) = (0.0, 0.0);
    for v in values {
        let dev = v - m;
        accum += dev * dev;
        accum2 += dev;
    }
    let n = n as f64;
    ((accum - (accum2 * accum2 / n)) / (n - 1.0)).sqrt()
}

/// GyroOnTheFlyCalModule / OnTheFlyGyroOffsetCal: while the device lies still, re-estimates the
/// gyro offset. Once a window of round(fs) samples is full, if every calibrated axis varies by
/// less than 1.2 deg/s, the mean of the raw values becomes the offset, from the next packet on.
#[derive(Debug, Default, Clone)]
pub struct GyroOnTheFly {
    pub window: usize,
    cal: [std::collections::VecDeque<f64>; 3],
    uncal: [std::collections::VecDeque<f64>; 3],
}

const OTF_THRESHOLD: f64 = 1.2;

impl GyroOnTheFly {
    /// setBufferSizeFromSamplingRate: Java's Math.round, so halves round up. Values already in
    /// the window are kept (newest first to go if it shrinks), as DescriptiveStatistics does.
    pub fn set_window(&mut self, sampling_rate: f64) {
        self.window = (sampling_rate + 0.5).floor() as usize;
        for d in self.cal.iter_mut().chain(self.uncal.iter_mut()) {
            while d.len() > self.window {
                d.pop_front();
            }
        }
    }

    pub fn update(
        &mut self,
        calibration: &mut KinematicCalibration,
        cal: [f64; 3],
        uncal: [f64; 3],
    ) {
        for axis in 0..3 {
            if self.cal[axis].len() == self.window {
                self.cal[axis].pop_front();
                self.uncal[axis].pop_front();
            }
            self.cal[axis].push_back(cal[axis]);
            self.uncal[axis].push_back(uncal[axis]);
        }
        if self.cal[1].len() == self.window
            && self
                .cal
                .iter()
                .all(|d| standard_deviation(d.iter()) < OTF_THRESHOLD)
        {
            calibration.update_offset([
                mean(self.uncal[0].iter()),
                mean(self.uncal[1].iter()),
                mean(self.uncal[2].iter()),
            ]);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rounding_matches_java_bigdecimal_half_up() {
        assert_eq!(precision_correction(0.07, 2), 0.07);
        assert_eq!(precision_correction(-0.99, 2), -0.99);
        assert_eq!(precision_correction(113.99, 2), 113.99);
        assert_eq!(precision_correction(327.67, 2), 327.67);
        assert_eq!(precision_correction(-32768.0, 0), -32768.0);
        // A true tie in binary rounds up, as HALF_UP does: 0.125 is exact.
        assert_eq!(precision_correction(0.125, 2), 0.13);
        assert_eq!(precision_correction(-0.125, 2), -0.13);
        // The double nearest 0.135 is just above it, and the one nearest 0.145 just below; BigDecimal
        // rounds the exact value, not the decimal it was written as.
        assert_eq!(precision_correction(0.135, 2), 0.14);
        assert_eq!(precision_correction(0.145, 2), 0.14);
        assert_eq!(precision_correction(2.675, 2), 2.67);
        assert_eq!(precision_correction(1.005, 2), 1.0);
        assert_eq!(precision_correction(9.995, 2), 9.99);
        assert_eq!(precision_correction(99.5, 0), 100.0);
    }
}
