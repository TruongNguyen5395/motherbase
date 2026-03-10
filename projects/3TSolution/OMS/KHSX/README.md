# Phòng Kế Hoạch Sản Xuất (KHSX) — Planning System

**Module:** KHSX
**Parent:** OMS (Operation Management System)
**Status:** Feature List — Drafting

## Scope

Phòng KHSX chịu trách nhiệm lập kế hoạch sản xuất, tạo và quản lý lệnh sản xuất, phân bổ xuống nhà máy, lập danh mục mua hàng, theo dõi tiến độ sản xuất & mua hàng, điều phối đóng gói và hỗ trợ xuất hàng.

## Feature Groups (8 modules)

| # | Module | Mô tả |
|---|--------|-------|
| 1 | Quản lý BOM Vật tư | Phân loại danh mục NVL, hàng hóa phụ, Vật Tư Tiêu Hao |
| 2 | Quản lý BOM Đóng Gói | BOM vật tư đóng gói |
| 3 | Triển Khai Đơn Hàng | Tiếp nhận và triển khai đơn hàng từ PKD |
| 4 | Quản Lý Lệnh Sản Xuất | Lập kế hoạch, phát lệnh, theo dõi LSX link với Triển khai đơn hàng |
| 5 | Quản lý yêu cầu Mua Hàng (Cho SX) | Tạo danh mục NVL, hàng hóa phụ, VTTH, vật tư đóng gói — theo dõi tiến độ mua hàng |
| 6 | Quản lý đóng gói | Xem tình hình SX (tự động nhóm BTP), đối chiếu vật tư đóng gói với hợp đồng — Tạo Lệnh |
| 7 | Quản lý xuất khẩu | Phối hợp xuất khẩu, chứng từ |
| 8 | Khiếu nại | Xử lý khiếu nại KH, sản xuất sửa/bù hàng |

## Stakeholders

| Bộ phận | Vai trò |
|---------|---------|
| Phòng KHSX | Lập kế hoạch, tạo lệnh SX, phân bổ, theo dõi tiến độ |
| Phòng Kinh Doanh | Cung cấp Bảng TKĐH, xác nhận khởi động SX |
| Phòng Thiết kế / Kỹ thuật | Bản vẽ, quy cách kỹ thuật, SX thử |
| Phòng Mua Hàng | Tiếp nhận PR, mua NVL/vật tư |
| Kho nhà máy | Nhập xuất vật tư, quản lý tồn kho |
| Nhà máy sản xuất | Thực hiện SX theo LSX |
| QA/QC | Kiểm tra chất lượng |
| Nhà máy đóng gói | Đóng gói, xuất kho |

## Workflow Reference

Based on: `Bảng mô tả các bước công việc phòng KHSX R1.4.pdf` (27 steps)

## Deliverables

- [x] Feature list draft → `feature-list.md`
- [ ] Feature list review & finalize
- [ ] Wireframes (Excalidraw)
- [ ] PRD/BRD (later phase)
