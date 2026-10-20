import type { ReactNode } from "react";
import type { LucideIcon } from "lucide-react";

export default function Section({
  icon: Icon,
  title,
  subtitle,
  children,
}: {
  icon?: LucideIcon;
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <section className="mt-10">
      <div className="flex items-baseline gap-2.5 mb-1">
        {Icon && <Icon size={17} className="text-ledger-gold shrink-0 translate-y-[1px]" />}
        <h2 className="font-display text-xl">{title}</h2>
      </div>
      {subtitle && <p className="text-sm text-ledger-faint mb-3">{subtitle}</p>}
      <div className={subtitle ? "" : "mt-3"}>{children}</div>
    </section>
  );
}
