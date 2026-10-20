import { afterEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import ReportPage from "../pages/ReportPage";
import MultiComparePage from "../pages/MultiComparePage";
import MethodologyPage from "../pages/MethodologyPage";
import WatchlistPage from "../pages/WatchlistPage";
import { mockFetch, report } from "./fixtures";

// recharts' ResponsiveContainer needs ResizeObserver, which jsdom lacks.
globalThis.ResizeObserver = class {
  observe() {}
  unobserve() {}
  disconnect() {}
} as unknown as typeof ResizeObserver;

afterEach(cleanup);

function at(path: string, route: string, el: React.ReactElement) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={route} element={el} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ReportPage", () => {
  it("renders the v1 sections plus the v2 additions", async () => {
    mockFetch({
      "/reports/latest/3": report,
      "/reports/what-changed/3": new Response(JSON.stringify({ message: "Need at least two" }), { status: 409 }),
      "/reports/history/3": [],
      "/watchlist/3": { watching: false },
    });
    at("/report/3", "/report/:companyId", <ReportPage />);

    expect(await screen.findByText("Tata Consultancy Services Ltd")).toBeTruthy();
    expect(screen.getByText("IT / IT Services", { exact: false })).toBeTruthy();
    expect(screen.getByText("-3.20 (-0.08%) today")).toBeTruthy();
    // a category with no data is labelled, not shown as 0/100
    expect(screen.getByText("No data yet, not counted in the overall score")).toBeTruthy();
    // AI-off note and rule-based summary note
    expect(screen.getByText(/qualitative checks .* didn't run/)).toBeTruthy();
    expect(screen.getByText(/fixed template/)).toBeTruthy();
    // 409 from what-changed is a first-report message, not an error
    expect(await screen.findByText(/first report for this company/)).toBeTruthy();
    // live peer we don't hold yet can be imported
    expect(screen.getByRole("button", { name: "Analyse" })).toBeTruthy();
    expect(await screen.findByRole("button", { name: "Add to watchlist" })).toBeTruthy();
  });

  it("shows the backend's error message", async () => {
    mockFetch({ "/reports/latest/9": new Response(JSON.stringify({ message: "No company with id 9" }), { status: 404 }) });
    at("/report/9", "/report/:companyId", <ReportPage />);
    expect(await screen.findByText("Couldn't generate a report: No company with id 9")).toBeTruthy();
  });

  it("regenerate forces a fresh report", async () => {
    const calls = mockFetch({
      "/reports/latest/3": report,
      "/reports/generate/3": { ...report, reportId: 13, overallScore: 70.1 },
      "/reports/what-changed/3": new Response("{}", { status: 409 }),
      "/reports/history/3": [],
      "/watchlist/3": { watching: true },
    });
    at("/report/3", "/report/:companyId", <ReportPage />);
    fireEvent.click(await screen.findByRole("button", { name: "Regenerate" }));
    await waitFor(() => expect(calls.some((c) => c.url.includes("/reports/generate/3?force=true"))).toBe(true));
  });

  it("what-if weights recompute the overall and exclude no-data categories", async () => {
    mockFetch({
      "/reports/latest/3": report,
      "/reports/what-changed/3": new Response("{}", { status: 409 }),
      "/reports/history/3": [],
      "/watchlist/3": { watching: false },
    });
    at("/report/3", "/report/:companyId", <ReportPage />);
    const slider = (await screen.findByLabelText("Valuation weight")) as HTMLInputElement;
    // Official weights, risk (no data) excluded: (90.6*20+93.8*20+71.2*15+30.1*20+88.6*10+75.4*5)/90
    expect(screen.getByText("73.6")).toBeTruthy();
    fireEvent.change(slider, { target: { value: "0" } });
    // without valuation: (90.6*20+93.8*20+71.2*15+88.6*10+75.4*5)/70
    expect(screen.getByText("86.0")).toBeTruthy();
    expect((screen.getByLabelText("Risk weight") as HTMLInputElement).disabled).toBe(true);
  });
});

describe("MultiComparePage", () => {
  it("marks the best value per row", async () => {
    mockFetch({
      "/companies/search": [],
      "/compare/multi?ids=1,2,3": {
        companies: [1, 2, 3].map((id) => ({
          company: { id, name: `Co ${id}`, nseSymbol: `C${id}`, bseCode: "", sector: "", industry: "" },
          overallScore: 60 + id, verdict: "🟡 Neutral–Watch",
        })),
        rows: [{ metricLabel: "P/E ratio", values: ["20", "15", "N/A"], bestIndices: [1], higherIsBetter: false }],
      },
    });
    at("/compare-multi?ids=1,2,3", "/compare-multi", <MultiComparePage />);
    expect(await screen.findByText("P/E ratio")).toBeTruthy();
    expect(screen.getByText("lower is better")).toBeTruthy();
    expect(screen.getAllByLabelText("best value")).toHaveLength(1);
  });

  it("asks for a second company", async () => {
    mockFetch({ "/companies/search": [] });
    at("/compare-multi?ids=1", "/compare-multi", <MultiComparePage />);
    expect(await screen.findByText(/Add at least two companies/)).toBeTruthy();
  });
});

describe("MethodologyPage", () => {
  it("renders bands and flags from the API", async () => {
    mockFetch({
      "/scoring/methodology": {
        categoryWeights: { financial_health: 0.2 },
        rules: [{ category: "financial_health", metricKey: "debt_to_equity", sector: null, thresholds: [0.5, 1], scores: [100, 85, 60], weight: 0.2, higherIsBetter: false }],
        flags: [{ flagKey: "moat_erosion", category: "business_quality", description: "Signals of competitive moat erosion detected", effectAtHighConfidence: -20 }],
        confidenceMultipliers: { high: 1, medium: 0.6, low: 0.3 },
        verdictBands: [{ minScore: 80, verdict: "🟢 Strong Candidate" }, { minScore: 0, verdict: "🔴 Weak Fundamentals" }],
        skipLeverageForFinancials: true,
      },
    });
    at("/methodology", "/methodology", <MethodologyPage />);
    expect(await screen.findByText("below 0.5 → 100 · 0.5 → 85 · 1 → 60")).toBeTruthy();
    expect(screen.getByText("-20")).toBeTruthy();
    expect(screen.getByText(/skipped for banks/)).toBeTruthy();
  });
});

describe("WatchlistPage", () => {
  it("lists items with their score change", async () => {
    mockFetch({
      "/companies/search": [],
      "/watchlist": [{
        company: { id: 1, name: "HDFC Bank Ltd", nseSymbol: "HDFCBANK", bseCode: "", sector: "", industry: "" },
        note: null, addedAt: "2026-10-01T00:00:00", latestScore: 74.1, latestVerdict: "🟢 Worth Researching",
        lastReportAt: "2026-10-12T00:00:00", scoreChange: -1.4, lastClose: 1650.95, lastCloseDate: "2026-09-01",
      }],
    });
    at("/watchlist", "/watchlist", <WatchlistPage />);
    expect(await screen.findByText("HDFC Bank Ltd")).toBeTruthy();
    expect(screen.getByText("74.1")).toBeTruthy();
    expect(screen.getByText("1.4")).toBeTruthy();
  });
});
