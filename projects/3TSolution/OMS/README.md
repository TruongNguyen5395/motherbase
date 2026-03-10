# OMS — Operation Management System

**Project:** FV-OMS (Factory ERP/ERM — Tầng 1: Quản Lý Điều Hành)
**Company:** 3TSolution / Kingston
**Status:** In Progress

## Overview

OMS is the Operation Management layer of the Kingston Factory ERP system. It covers all business operation modules including sales, planning, purchasing, document management, analytics, and workflow.

## Architecture Position

OMS = **Tầng 1: Quản Lý Điều Hành (Operation Management)** in the Factory ERP/ERM layered architecture.

## Modules

| # | Module | Vietnamese | Status |
|---|--------|-----------|--------|
| 1 | Sale System | Phòng Kinh Doanh (PKD) | Done |
| 2 | Planning System | Phòng Kế Hoạch Sản Xuất (KHSX) | In Progress |
| 3 | Purchasing System | Phòng Mua Hàng | Not Started |
| 4 | Document Management | Quản Lý Tài Liệu | Not Started |
| 5 | Business Analytics & AI | Báo Cáo và AI | Not Started |
| 6 | Workflow & Process Management | Quản Lý Luồng Công Việc | Not Started |

## Deliverables per Module

1. **Feature List** — Tổng hợp tính năng (Excel format)
2. **Wireframes** — UI mockups (Excalidraw)
3. **PRD/BRD** — Product/Business Requirements Document (later phase)

## File Structure

```
OMS/
├── README.md
├── PKD/                    # Phòng Kinh Doanh (Done)
│   └── (feature list in .tmp/)
├── KHSX/                   # Phòng Kế Hoạch Sản Xuất
│   ├── README.md
│   └── feature-list.md
└── ...                     # Future modules
```

## References

- Architecture doc: `templates/claudecode/Init CC project.md`
- PKD feature list: `.tmp/Tổng hợp tính năng module PKD FV-OMS R1.0.xlsx`
- KHSX workflow: `Bảng mô tả các bước công việc phòng KHSX R1.4.pdf`
