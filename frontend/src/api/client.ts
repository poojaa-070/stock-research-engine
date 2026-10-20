import type {
  CompanySummary,
  ReportResponse,
  CompareResponse,
  MultiCompareResponse,
  ReportHistoryPoint,
  WhatChanged,
  WatchlistItem,
  Methodology,
  Health,
} from "../types";

export const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080/api";

// No demo/fixture fallback anywhere in this file, on purpose: an earlier
// version quietly substituted bundled fake data whenever the backend call
// failed, with no indication on screen that what was shown wasn't real.
// Every function below either returns real backend data or throws.

/** An error from the API, carrying the HTTP status and the backend's own message. */
export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, init);
  } catch {
    // fetch only rejects on network failure (backend down, CORS, wrong URL).
    throw new ApiError(0, `Can't reach the backend at ${API_BASE}. Check that the Spring Boot API is running.`);
  }
  if (!res.ok) {
    // The backend's GlobalExceptionHandler returns {status, error, message, ...}.
    let message = `Request failed with status ${res.status}.`;
    try {
      const body = await res.json();
      if (body && typeof body.message === "string" && body.message) message = body.message;
    } catch {
      /* non-JSON error body: keep the generic message */
    }
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : "Something went wrong.";
}

// ---- companies ----
export const searchCompanies = (query: string) =>
  request<CompanySummary[]>(`/companies/search?q=${encodeURIComponent(query)}`);

// Real-time lookup: only call this when local search comes up empty and the
// person explicitly asks to import. Hits Screener via the Python scraper.
export const importCompany = (query: string) =>
  request<CompanySummary>(`/companies/import?query=${encodeURIComponent(query)}`, { method: "POST" });

// ---- reports ----
/** Always a new pass (re-scrape + re-score). force=false lets the backend reuse a report from the last minute. */
export const generateReport = (companyId: number, force = false) =>
  request<ReportResponse>(`/reports/generate/${companyId}${force ? "?force=true" : ""}`, { method: "POST" });

/** What the report page loads: a report from the last 15 minutes if one exists, else a fresh one. */
export const getLatestReport = (companyId: number) => request<ReportResponse>(`/reports/latest/${companyId}`);

export const getReportHistory = (companyId: number, limit = 30) =>
  request<ReportHistoryPoint[]>(`/reports/history/${companyId}?limit=${limit}`);

/** Resolves to null (not an error) when fewer than two reports exist yet. */
export async function getWhatChanged(companyId: number): Promise<WhatChanged | null> {
  try {
    return await request<WhatChanged>(`/reports/what-changed/${companyId}`);
  } catch (e) {
    if (e instanceof ApiError && e.status === 409) return null;
    throw e;
  }
}

// ---- compare ----
export const compareCompanies = (aId: number, bId: number) =>
  request<CompareResponse>(`/compare?a=${aId}&b=${bId}`);

// Live compare-by-name: two free-text names, resolved + freshly scraped on the backend.
export const compareCompaniesByName = (nameA: string, nameB: string) =>
  request<CompareResponse>(`/compare/live?a=${encodeURIComponent(nameA)}&b=${encodeURIComponent(nameB)}`);

export const compareMany = (ids: number[]) => request<MultiCompareResponse>(`/compare/multi?ids=${ids.join(",")}`);

// ---- watchlist ----
export const getWatchlist = () => request<WatchlistItem[]>(`/watchlist`);
export const isWatching = (companyId: number) =>
  request<{ watching: boolean }>(`/watchlist/${companyId}`).then((r) => r.watching);
export const addToWatchlist = (companyId: number, note?: string) =>
  request<WatchlistItem>(`/watchlist/${companyId}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(note === undefined ? {} : { note }),
  });
export const removeFromWatchlist = (companyId: number) =>
  request<void>(`/watchlist/${companyId}`, { method: "DELETE" });

// ---- meta ----
export const getMethodology = () => request<Methodology>(`/scoring/methodology`);

/** Health is reported even when the DB is down (HTTP 503 with a body). */
export async function getHealth(): Promise<Health | null> {
  try {
    const res = await fetch(`${API_BASE}/health`);
    return (await res.json()) as Health;
  } catch {
    return null;
  }
}
