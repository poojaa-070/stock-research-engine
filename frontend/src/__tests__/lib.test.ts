import { describe, expect, it } from "vitest";
import { reportToMarkdown } from "../lib/reportMarkdown";
import { crore, pct, timeAgo } from "../lib/format";
import { ApiError, getWhatChanged } from "../api/client";
import { mockFetch, report } from "./fixtures";

describe("reportToMarkdown", () => {
  it("includes the score, every evidence line with its source, and the disclaimer", () => {
    const md = reportToMarkdown(report);
    expect(md).toContain("# Tata Consultancy Services Ltd (TCS)");
    expect(md).toContain("**Overall score:** 76.2/100");
    expect(md).toContain("- Debt-to-equity is 0.02 -> scored 100/100 _(source: ratios table, latest period)_");
    expect(md).toContain("### Risk: no data (0% data)");
    expect(md.trim().endsWith("_This is an analytical research tool, not personalized financial advice._")).toBe(true);
    expect(md).not.toMatch(/\n\n\n/);
  });
});

describe("format", () => {
  it("formats crore and percentages", () => {
    expect(crore(262000)).toBe("₹2.62 lakh Cr");
    expect(crore(4400)).toBe("₹4,400 Cr");
    expect(pct(4.2, 1, true)).toBe("+4.2%");
    expect(pct(null)).toBe("—");
    expect(timeAgo(null)).toBe("never");
  });
});

describe("api client", () => {
  it("turns a 409 from what-changed into null", async () => {
    mockFetch({ "what-changed": new Response("{}", { status: 409 }) });
    expect(await getWhatChanged(1)).toBeNull();
  });

  it("surfaces the backend message and status on other errors", async () => {
    mockFetch({ "what-changed": new Response(JSON.stringify({ message: "boom" }), { status: 502 }) });
    await expect(getWhatChanged(1)).rejects.toEqual(new ApiError(502, "boom"));
  });

  it("explains a network failure", async () => {
    globalThis.fetch = (async () => { throw new TypeError("Failed to fetch"); }) as typeof fetch;
    await expect(getWhatChanged(1)).rejects.toThrow(/Can't reach the backend/);
  });
});
