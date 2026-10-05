// Runs the core's WebAssembly build in Node (a JavaScript engine, as a browser has) on the two
// recordings, and checks it frames the same packets as the Java decoder.
//
//   cargo rustc --lib --release --target wasm32-unknown-unknown --crate-type cdylib
//   node wasm/run_framing.mjs

import { readFileSync } from "node:fs";

const here = new URL("..", import.meta.url);
const repo = new URL("../", here);
const wasm = readFileSync(new URL("target/wasm32-unknown-unknown/release/shimmer_protocol.wasm", here));
const { instance } = await WebAssembly.instantiate(wasm, {});
const core = instance.exports;

const PACKET_SIZE = 23;
const CRC_BYTES = 1;

/** The notifications after START_STREAMING's TX, up to the next TX. */
function streamNotifications(recording) {
  const out = [];
  let afterStart = false;
  for (const line of readFileSync(new URL(recording, repo), "utf8").split(/\r?\n/)) {
    const m = line.match(/ (TX|RX) h\d+ \[([^\]]*)\]/);
    if (!m) continue;
    const bytes = m[2].trim().split(/\s+/).map((h) => parseInt(h, 16));
    if (m[1] === "TX") {
      if (afterStart) break;
      afterStart = bytes[0] === 0x07;
    } else if (afterStart) {
      out.push(Uint8Array.from(bytes));
    }
  }
  return out;
}

function javaTimestamps(reference) {
  const [header, ...rows] = readFileSync(new URL(reference, repo), "utf8").trim().split(/\r?\n/);
  const column = header.split(",").indexOf("Timestamp|UNCAL|Ticks");
  return rows.map((r) => Number(r.split(",")[column]));
}

function frame(notifications) {
  const framer = core.shimmer_framer_new(PACKET_SIZE, CRC_BYTES);
  core.shimmer_framer_expect_ack(framer);
  const outPackets = 64;
  const out = core.shimmer_alloc(outPackets * PACKET_SIZE);
  const timestamps = [];
  for (const n of notifications) {
    const input = core.shimmer_alloc(n.length);
    new Uint8Array(core.memory.buffer, input, n.length).set(n);
    const count = core.shimmer_framer_push(framer, input, n.length, out, outPackets);
    // Views are taken after the call: the core's memory may have grown.
    const packets = new Uint8Array(core.memory.buffer, out, outPackets * PACKET_SIZE);
    for (let i = 0; i < count; i++) {
      const p = i * PACKET_SIZE;
      timestamps.push(packets[p] | (packets[p + 1] << 8) | (packets[p + 2] << 16));
    }
    core.shimmer_free(input, n.length);
  }
  core.shimmer_free(out, outPackets * PACKET_SIZE);
  core.shimmer_framer_free(framer);
  return timestamps;
}

let failed = false;
for (const [device, recording, reference] of [
  ["Shimmer3R", "ShimmerDriverPC/src/test/resources/protocol/shimmer3r_2f31_handshake_stream10s.bytes.log", "python/tests/data/java_reference.csv"],
  ["Shimmer3", "ShimmerDriverPC/src/test/resources/protocol/shimmer3_3e36_handshake_stream10s.bytes.log", "python/tests/data/java_reference_shimmer3.csv"],
]) {
  const mine = frame(streamNotifications(recording));
  const java = javaTimestamps(reference);
  const same = mine.length === java.length && mine.every((t, i) => t === java[i]);
  console.log(`${device}: WebAssembly framed ${mine.length} packets, Java decoded ${java.length}: ${same ? "identical" : "DIFFERENT"}`);
  failed ||= !same;
}

// The checksum, through the C ABI: the device's ACK checksum and a value from the Java ShimmerCrc.
const buf = core.shimmer_alloc(4);
new Uint8Array(core.memory.buffer, buf, 4).set([0xff, 0x02, 0x80, 0x02]);
const ack = core.shimmer_crc(buf, 1) & 0xff;
const java = core.shimmer_crc(buf, 4);
core.shimmer_free(buf, 4);
console.log(`checksum: ACK 0x${ack.toString(16)} (device: 0xf4), [ff 02 80 02] 0x${java.toString(16)} (Java: 0xefe)`);
failed ||= ack !== 0xf4 || java !== 0x0efe;

console.log(`WebAssembly module: ${wasm.length} bytes`);
process.exit(failed ? 1 : 0);
