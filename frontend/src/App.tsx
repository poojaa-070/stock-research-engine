import { lazy, Suspense } from "react";
import { BrowserRouter, Routes, Route, Link } from "react-router-dom";
import Header from "./components/Header";
import SearchPage from "./pages/SearchPage";

// v2: every page except search is loaded on demand, so the charting library
// isn't in the first download (the single v1 bundle was over 500 kB).
const ReportPage = lazy(() => import("./pages/ReportPage"));
const ComparePage = lazy(() => import("./pages/ComparePage"));
const LiveComparePage = lazy(() => import("./pages/LiveComparePage"));
const MultiComparePage = lazy(() => import("./pages/MultiComparePage"));
const WatchlistPage = lazy(() => import("./pages/WatchlistPage"));
const MethodologyPage = lazy(() => import("./pages/MethodologyPage"));

function NotFound() {
  return (
    <div className="max-w-3xl mx-auto pt-16 px-6">
      <p className="text-ledger-faint">There's no page at this address.</p>
      <Link to="/" className="inline-block mt-3 text-sm text-ledger-gold hover:underline">Go to search</Link>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-ledger-hero">
        <Header />
        <Suspense fallback={<p className="max-w-3xl mx-auto pt-16 px-6 text-sm text-ledger-faint">Loading…</p>}>
        <Routes>
          <Route path="/" element={<SearchPage />} />
          <Route path="/report/:companyId" element={<ReportPage />} />
          <Route path="/compare/:aId/:bId" element={<ComparePage />} />
          <Route path="/compare-live" element={<LiveComparePage />} />
          <Route path="/compare-multi" element={<MultiComparePage />} />
          <Route path="/watchlist" element={<WatchlistPage />} />
          <Route path="/methodology" element={<MethodologyPage />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
        </Suspense>
      </div>
    </BrowserRouter>
  );
}
