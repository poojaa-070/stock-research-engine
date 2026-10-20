import { Download, Printer, FileJson } from "lucide-react";
import type { ReportResponse } from "../types";
import { download, reportToMarkdown } from "../lib/reportMarkdown";

export default function ExportMenu({ report }: { report: ReportResponse }) {
  const base = `${report.nseSymbol || "report"}-${report.generatedAt.slice(0, 10)}`;
  const btn = "inline-flex items-center gap-1.5 text-xs text-ledger-faint hover:text-ledger-ink border border-ledger-line px-2.5 py-1.5";
  return (
    <div className="flex flex-wrap gap-2 print:hidden">
      <button className={btn} onClick={() => download(`${base}.md`, reportToMarkdown(report), "text/markdown")}>
        <Download size={13} /> Markdown
      </button>
      <button className={btn} onClick={() => download(`${base}.json`, JSON.stringify(report, null, 2), "application/json")}>
        <FileJson size={13} /> JSON
      </button>
      <button className={btn} onClick={() => window.print()}>
        <Printer size={13} /> Print / PDF
      </button>
    </div>
  );
}
