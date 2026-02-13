import * as React from "react";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface NavItem {
  label: string;
  href: string;
  icon: React.ReactNode;
  badge?: string | number;
  active?: boolean;
}

interface SidebarProps {
  logo?: React.ReactNode;
  items: NavItem[];
  collapsed?: boolean;
  onToggle?: () => void;
  className?: string;
}

function Sidebar({ logo, items, collapsed = false, onToggle, className }: SidebarProps) {
  return (
    <aside
      className={cn(
        "flex flex-col h-screen bg-white border-r border-slate-200 transition-all duration-300",
        collapsed ? "w-16" : "w-56",
        className
      )}
    >
      {/* Logo 区域 */}
      <div className="h-14 flex items-center px-4 border-b border-slate-100">
        {logo || (
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-primary-600 flex items-center justify-center">
              <svg className="w-5 h-5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </div>
            {!collapsed && <span className="font-semibold text-slate-900">Agent</span>}
          </div>
        )}
      </div>

      {/* 导航菜单 */}
      <nav className="flex-1 py-4 px-2 space-y-1 overflow-y-auto">
        {items.map((item, index) => (
          <a
            key={index}
            href={item.href}
            className={cn(
              "flex items-center gap-3 px-3 py-2 rounded-md text-sm font-medium transition-colors",
              item.active
                ? "bg-primary-50 text-primary-700"
                : "text-slate-600 hover:bg-slate-50 hover:text-slate-900",
              collapsed && "justify-center px-2"
            )}
            title={collapsed ? item.label : undefined}
          >
            <span className={cn("shrink-0", item.active && "text-primary-600")}>
              {item.icon}
            </span>
            {!collapsed && (
              <>
                <span className="flex-1">{item.label}</span>
                {item.badge && (
                  <span className="px-2 py-0.5 text-xs bg-slate-100 text-slate-600 rounded-full">
                    {item.badge}
                  </span>
                )}
              </>
            )}
          </a>
        ))}
      </nav>

      {/* 折叠按钮 */}
      <div className="p-2 border-t border-slate-100">
        <Button
          variant="ghost"
          size="icon"
          onClick={onToggle}
          className="w-full"
        >
          {collapsed ? (
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 5l7 7-7 7M5 5l7 7-7 7" />
            </svg>
          ) : (
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
            </svg>
          )}
        </Button>
      </div>
    </aside>
  );
}

// 简洁版 Sidebar（用于对话页）
interface CompactSidebarProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  headerAction?: React.ReactNode;
  className?: string;
}

function CompactSidebar({ title, subtitle, children, headerAction, className }: CompactSidebarProps) {
  return (
    <div className={cn("flex flex-col h-full bg-white border-r border-slate-200", className)}>
      {/* 头部 */}
      <div className="p-4 border-b border-slate-100">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="font-semibold text-slate-900">{title}</h3>
            {subtitle && <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>}
          </div>
          {headerAction}
        </div>
      </div>

      {/* 内容 */}
      <div className="flex-1 overflow-y-auto p-3">{children}</div>
    </div>
  );
}

export { Sidebar, CompactSidebar };
export type { NavItem };
