package com.shimmerresearch.simpleexamples.bletest;

import java.util.ArrayList;
import java.util.List;

/** The outcome of one test, with the measurements behind it. */
public class TestResult {

	public enum Outcome {
		PASS, FAIL, SKIPPED
	}

	public final String test;
	public Outcome outcome = Outcome.FAIL;
	/** One line for the summary table. */
	public String summary = "";
	public final List<String> details = new ArrayList<String>();
	public long durationMs;

	public TestResult(String test) {
		this.test = test;
	}

	public TestResult pass(String summary) {
		this.outcome = Outcome.PASS;
		this.summary = summary;
		return this;
	}

	public TestResult fail(String summary) {
		this.outcome = Outcome.FAIL;
		this.summary = summary;
		return this;
	}

	public TestResult skip(String summary) {
		this.outcome = Outcome.SKIPPED;
		this.summary = summary;
		return this;
	}

	public void detail(String line) {
		details.add(line);
		System.out.println("  [" + test + "] " + line);
	}
}
