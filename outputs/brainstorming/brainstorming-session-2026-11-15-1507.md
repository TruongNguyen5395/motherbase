---
stepsCompleted: [1, 2, 3, 4]
session_active: false
workflow_completed: true
inputDocuments:
  - /home/ubuntu/work/hidave/hermes/data/skills/telegram-inbox-buttons/SKILL.md
  - /home/ubuntu/work/hidave/hermes/data/skills/reply-on-behalf/SKILL.md
  - /home/ubuntu/work/hidave/hermes/data/plugins/inbox-buttons/__init__.py
  - /home/ubuntu/work/hidave/hermes/data/plugins/herald-push/README.md
  - /home/ubuntu/work/hidave/motherbase/shared/core/src/commonMain/kotlin/dev/hermeskotlin/core/inbox/Inbox.kt
session_topic: 'Kiến trúc MotherBase Action Inbox theo mô hình Decision Triage Queue (xử lý vấn đề dạng topic độc lập và tự dọn sạch sau khi hoàn tất)'
session_goals: 'Thiết kế luồng đẩy mọi vấn đề phát sinh từ các kênh (Zalo, Lark, Mail, MCP) vào Inbox dạng đơn vị công việc độc lập (như topic); bấm vào xử lý trực tiếp, hoàn tất thì xóa khỏi hàng đợi; đảm bảo khả năng mở rộng lâu dài cho hệ sinh thái MotherBase (Android Mobile App).'
selected_approach: 'ai-recommended'
techniques_used: ['First Principles Thinking', 'Morphological Analysis', 'Chaos Engineering & Failure Analysis']
ideas_generated:
  - 'Architecture #1: Solution-First Action Sheet'
  - 'Extensibility #2: Channel-Agnostic Inbox Envelope'
  - 'Plugin System #3: Two-Way Channel Adapter Registry'
  - 'Performance #4: Zero-Wait Pre-Computed Solutions'
  - 'Plugin Architecture #5: Universal Action Payload Contract'
  - 'Lifecycle #6: Ephemeral Issue-Topic Lifecycle'
  - 'Deduplication #7: Dedup Key & Live Issue Upsert'
  - 'UX Architecture #8: Three-Tier Issue Sheet'
  - 'Plugin Specification #9: Declarative Channel Manifest'
  - 'Interactive Widgets #10: Progressive Solution Rendering (Text Draft -> Embedded A2UI)'
  - 'Resilience #11: Optimistic Triage with 3-Second Undo & Auto-Bounce on Failure'
  - 'Escalation Flow #12: Seamless Escalation to Full Linked Chat (Cách 2B)'
context_file: ''
---

# Brainstorming Session Results

**Facilitator:** Trường Nguyễn
**Date:** 2026-11-15T15:07:28+07:00

## Session Overview

**Topic:** Kiến trúc MotherBase Action Inbox theo mô hình Decision Triage Queue (Hàng đợi xử lý quyết định & tác vụ dạng topic độc lập)
**Goals:**
1. Khi bất kỳ vấn đề/thông báo nào cần tương tác từ đa kênh (Zalo, Lark, Gmail, Lark Mail, các MCP hệ thống khác) xuất hiện, tự động tạo thành một mục độc lập trong Inbox.
2. Cho phép bấm trực tiếp vào vấn đề đó để thấy ngay giải pháp đề xuất sẵn của Agent (chọn phương án, sửa bản nháp, hoặc bấm nhảy sang khung Chat chuẩn khi vấn đề khó cần đi sâu).
3. Sau khi xử lý xong (Resolve/Done), xóa khỏi Inbox (kèm nút Undo 3 giây) để giữ hàng đợi luôn sạch.
4. Thiết kế kiến trúc Channel theo dạng Plugin chuẩn hóa để người dùng hoặc người khác dễ dàng tích hợp thêm kênh mới về sau mà không cần sửa code Mobile App.

---

## Idea Generation Log

### Technique 1: First Principles Thinking

**[Architecture #1]**: Solution-First Action Sheet (Phiếu Giải Pháp Ưu Tiên)
_Concept_: Mỗi mục trong Inbox mở ra dưới dạng một Action Sheet hiển thị ngay tóm tắt vấn đề và các phương án giải quyết cụ thể do Agent đề xuất (kèm bản nháp điền sẵn nếu là tác vụ phản hồi), chỉ mở luồng hội thoại sâu khi người dùng chủ động yêu cầu bàn thêm.
_Novelty_: Đảo ngược mô hình chat truyền thống thành mô hình duyệt quyết định (Agent làm sẵn 80% việc, người dùng chỉ duyệt/sửa 20% cuối rồi xóa khỏi hàng đợi).

**[Extensibility #2]**: Channel-Agnostic Inbox Envelope (Phong Bì Dữ Liệu Đa Kênh Chuẩn Hóa)
_Concept_: Định nghĩa một schema JSON thống nhất (`InboxIssue`) đóng vai trò giao thức trung gian giữa mọi nguồn dữ liệu (Zalo, Lark, Gmail, MCP...) và ứng dụng MotherBase. Ứng dụng di động không cần biết logic riêng của Zalo hay Lark mà chỉ render dựa trên khai báo của Envelope.
_Novelty_: Cho phép thêm bất kỳ kênh mới nào ở phía Server/Plugin mà không cần sửa code hay cập nhật lại ứng dụng Android.

**[Plugin System #3]**: Two-Way Channel Adapter Registry (Hệ Thống Plugin Kênh 2 Chiều: Ingest & Act)
_Concept_: Mỗi kênh tích hợp được đóng gói thành một Plugin độc lập theo chuẩn 2 chiều: chiều thu thập (`ingest`: lắng nghe webhook/polling và chuẩn hóa thành `InboxIssue`) và chiều thực thi (`act`: nhận quyết định/bản nháp đã duyệt từ người dùng để bắn ngược lại API của kênh gốc như gửi Zalo, gửi Mail, cập nhật trạng thái issue PM).
_Novelty_: Biến Inbox từ nơi "chỉ đọc thông báo" thành một bus điều phối hành động khép kín (Closed-loop Action Bus) mà người khác có thể viết thêm adapter mới chỉ bằng 1 file cấu hình + script.

**[Performance #4]**: Zero-Wait Pre-Computed Solutions (Giải Pháp Tính Toán Sẵn Từ Vòng Quét)
_Concept_: Ngay tại thời điểm Agent quét kênh ngầm và phát hiện vấn đề, Agent sinh sẵn toàn bộ payload thực thi (bản nháp tin nhắn theo đúng văn phong, hoặc tham số lệnh MCP/tool) đính kèm vào từng phương án A/B/C. Khi người dùng mở mục Inbox, giải pháp hiển thị tức thì để bấm duyệt trong 0.5 giây mà không cần gọi lại LLM lần 2.
_Novelty_: Loại bỏ độ trễ 10–15 giây chờ Agent suy nghĩ sau khi bấm nút và cắt giảm khoảng 2/3 số lượt gọi LLM cho một tác vụ Inbox thông thường.

**[Plugin Architecture #5]**: Universal Action Payload Contract (Hợp Đồng Thực Thi Đa Năng Cho Mọi Nguồn)
_Concept_: Bất kể nguồn phát sinh là tin nhắn (Zalo, Lark, Mail) hay phi-tin-nhắn (PM 3TN, Directus, Server Alert), mỗi phương án giải pháp (`SolutionOption`) đều tuân theo cấu trúc chuẩn: `label` (tên hành động), `editable_preview` (nội dung bản nháp hoặc tham số lệnh có thể chỉnh sửa nhanh trên UI), và `executor` (lệnh thực thi trực tiếp của plugin hoặc giao cho Agent).
_Novelty_: Thống nhất trải nghiệm duyệt giữa "trả lời tin nhắn" và "thực thi thay đổi hệ thống qua MCP" trên cùng một mẫu Action Sheet duy nhất.

**[Lifecycle #6]**: Ephemeral Issue-Topic Lifecycle (Vòng Đời Vấn Đề Tự Dọn Sạch)
_Concept_: Mỗi vấn đề trong Inbox mang trạng thái vòng đời rõ ràng (`open` -> `executing` -> `resolved`). Ngay khi phương án được chọn thực thi thành công hoặc người dùng bấm bỏ qua, vấn đề tự động đóng và xóa khỏi danh sách hiển thị.
_Novelty_: Đảm bảo Inbox phản ánh chính xác số việc đang chờ người dùng quyết định tại thời điểm hiện tại.

### Technique 2: Morphological Analysis

**[Deduplication #7]**: Dedup Key & Live Issue Upsert (Gộp Tự Động Theo Đầu Việc Đang Mở)
_Concept_: Mỗi vấn đề từ Channel Plugin mang một khóa định danh nguồn `dedup_key` (ví dụ: `zalo:<thread_id>`, `lark:<chat_id>`, `pm3tn:<issue_id>`). Khi phát sinh tin nhắn hoặc sự kiện mới có cùng `dedup_key` trong lúc vấn đề cũ vẫn đang ở trạng thái `open`, hệ thống tự động gộp nội dung mới vào vấn đề đang mở và cập nhật lại bản tóm tắt cùng bộ giải pháp `A/B/C` thay vì tạo mục mới.
_Novelty_: Giải quyết bài toán nhắn tin ngắt quãng nhiều câu trên Zalo/Lark, đảm bảo mỗi người/đầu việc chỉ chiếm đúng 1 dòng trong Inbox với phương án giải quyết luôn cập nhật theo tin nhắn cuối cùng.

**[UX Architecture #8]**: Three-Tier Issue Sheet (Bố Cục Phiếu Xử Lý 3 Tầng: Ngữ Cảnh -> Giải Pháp Dựng Sẵn -> Nút Nhảy Chat)
_Concept_: Giao diện khi bấm vào một vấn đề gồm: Tầng 1 (Ngữ cảnh & Xem tin gốc), Tầng 2 (Các phương án A/B/C kèm khung sửa nhanh bản nháp và nút Thực thi & Xóa), Tầng 3 (Nút Đi sâu cùng Agent để nhảy sang màn hình Chat đầy đủ khi gặp ca khó).
_Novelty_: Giữ nguyên luồng thao tác 1 chạm cho các ca đơn giản nhưng kết nối liền mạch với khung Chat chính khi gặp ca khó.

**[Plugin Specification #9]**: Declarative Channel Manifest (`inbox-channels/<id>/manifest.json`)
_Concept_: Chuẩn hóa mỗi kênh tích hợp thành một thư mục plugin độc lập có `manifest.json` khai báo nhãn, màu sắc, icon và danh sách các `action_type` mà kênh đó có thể thực thi tự động.
_Novelty_: Cho phép bất kỳ ai tạo thêm kênh mới (MCP, Webhook, Script) chỉ bằng cách thêm thư mục plugin trên server mà App MotherBase tự động nhận diện và hiển thị đúng giao diện.

**[Interactive Widgets #10]**: Progressive Solution Rendering (Text Draft -> Embedded A2UI Form trên Mobile)
_Concept_: Chỉ tập trung cho Mobile App ở giai đoạn hiện tại (tạm gác Wear OS để giảm độ phức tạp). Trên phiếu xử lý của Mobile, mỗi giải pháp mặc định hiển thị khung sửa nhanh văn bản (`draft_text`), nhưng hỗ trợ mở rộng thêm khối widget `a2ui` tùy chọn (dropdown chọn người, chọn ngày giờ, checkbox) khi kênh tích hợp là các hệ thống quản trị/MCP.
_Novelty_: Tái sử dụng trực tiếp bộ renderer A2UI v0.9 có sẵn trong `androidApp` ngay bên trong phiếu Inbox mà không phải viết UI riêng cho từng loại MCP sau này.

### Technique 3: Chaos Engineering & Failure Analysis

**[Resilience #11]**: Optimistic Triage with 3-Second Undo & Auto-Bounce on Failure (Ẩn Tức Thì Kèm Hoàn Tác Ngắn)
_Concept_: Khi người dùng bấm Thực thi hoặc Xóa một vấn đề, mục đó biến mất ngay lập tức khỏi danh sách Inbox để không cản trở thao tác tiếp theo, đồng thời hiện thanh Undo 3 giây ở đáy màn hình. Sau khi hết thời gian Undo, lệnh mới thực sự bắn đi; nếu Channel Plugin thực thi lỗi ở phía server, vấn đề tự động bật ngược lại lên đầu danh sách Inbox kèm thông báo lỗi và giữ nguyên nội dung bản nháp đã sửa.
_Novelty_: Đạt tốc độ phản hồi giao diện tức thì (0ms perceived latency) nhưng không bao giờ làm rơi mất tác vụ khi kênh ngoại vi (Zalo/Lark/Mail) bị đứt kết nối.

**[Escalation Flow #12]**: Seamless Escalation to Full Linked Chat (`Cách 2B` — Nhảy Thẳng Sang Khung Chat Chuẩn Gắn Kèm Nút Đóng Vấn Đề)
_Concept_: Thay vì nhồi nhét khung chat thu nhỏ vào trong thẻ Inbox, khi gặp vấn đề khó cần bàn sâu, nút `[Đi sâu cùng Agent]` chuyển thẳng người dùng sang màn hình Chat đầy đủ của MotherBase (hỗ trợ đầy đủ mic giọng nói, đính kèm ảnh, tiến trình chạy tool, A2UI) với ngữ cảnh vấn đề đã nạp sẵn và gắn `issue_id`. Trên đầu màn hình Chat hiển thị thanh trạng thái của vấn đề kèm nút `[Hoàn tất & Xóa khỏi Inbox]`.
_Novelty_: Giữ cho thẻ Inbox cực kỳ gọn nhẹ (chỉ tập trung vào duyệt giải pháp có sẵn), trong khi lúc cần đào sâu lại tận dụng được 100% sức mạnh sẵn có của màn hình Chat chính trong MotherBase.

---

## Idea Organization and Prioritization

### Thematic Organization

1. **Chủ đề 1: Giao thức & Hệ sinh thái Channel Plugin (`#2`, `#3`, `#5`, `#9`)**
   - Định nghĩa chuẩn `InboxIssue` JSON và thư mục plugin `inbox-channels/<channel_id>/`.
   - Tách biệt hoàn toàn logic của từng kênh (Zalo, Lark, Gmail, MCP...) khỏi ứng dụng Android.
2. **Chủ đề 2: Động cơ Hàng đợi Thông minh trên Server (`#4`, `#6`, `#7`)**
   - Tự động gộp tin nhắn trùng nguồn đang mở (`dedup_key`).
   - Agent tính toán sẵn giải pháp + bản nháp ngay từ vòng quét đầu tiên (`Pre-Computed Solutions`).
   - Vòng đời tự dọn sạch sau khi hoàn tất (`open` -> `executing` -> `resolved`).
3. **Chủ đề 3: Trải nghiệm Tương tác trên Mobile App MotherBase (`#1`, `#8`, `#10`, `#11`, `#12`)**
   - Bỏ rào cản bắt buộc `Notifications anywhere` mới cho vào Inbox (mở Inbox trực tiếp qua kết nối Gateway).
   - Phiếu xử lý ưu tiên giải pháp (`Action Sheet`) với ô sửa nhanh bản nháp + hỗ trợ A2UI widget.
   - Ẩn tức thì kèm thanh `Undo 3s` + tự bật lại nếu lỗi (`Auto-Bounce`).
   - Nút `[Đi sâu cùng Agent]` nhảy thẳng sang khung Chat chuẩn kèm thanh `[Hoàn tất & Xóa vấn đề]` ở đầu màn hình Chat.

### Action Planning (Lộ trình triển khai 3 Pha)

#### Pha 1: Xây dựng Core Action Inbox Engine & Chuẩn Channel Plugin trên Server (`hermes`)
- **Mục tiêu**: Tạo kho lưu trữ và điều phối `InboxIssue` mới (tương thích ngược với `ibx_store.json` của Telegram) và chuẩn hóa thư mục `inbox-channels/`.
- **Các bước cụ thể**:
  1. Thiết kế schema `InboxIssue` (hỗ trợ `id`, `dedup_key`, `channel`, `title`, `summary`, `raw_content`, `status`, `error`, `linked_session_id`, `solutions: [{id, label, draft_text, a2ui, executor}]`).
  2. Xây dựng bộ SDK/CLI chuẩn `/opt/data/scripts/inbox/mb_inbox.py` (hỗ trợ `upsert` theo `dedup_key`, `list`, `execute`, `resolve`, `link_session`) và mở rộng RPC trong plugin `herald-push` (`/herald-push inbox ...`) để App MotherBase gọi trực tiếp qua WebSocket mà không bắt buộc phải qua `device_id` của push.
  3. Đóng gói 4 kênh hiện tại (`zalo`, `lark`, `gmail`, `lark-mail`) thành 4 plugin mẫu trong `/opt/data/inbox-channels/` và cập nhật prompt quét kênh của Miller để sinh sẵn `draft_text` trong từng giải pháp `A/B/C`.

#### Pha 2: Xây dựng Giao diện Action Inbox mới trên MotherBase (`androidApp` + `shared`)
- **Mục tiêu**: Thay thế màn hình Inbox chỉ đọc log cron cũ bằng **Action Inbox (Decision Triage Queue)** trên Mobile.
- **Các bước cụ thể**:
  1. Gỡ bỏ màn hình chặn `inbox_needs_push` trong `InboxPage.kt` — cho phép tải danh sách vấn đề ngay khi kết nối Gateway.
  2. Xây dựng danh sách `InboxIssueCard` (hiển thị badge kênh màu sắc động từ plugin manifest, tiêu đề, bản tóm tắt của Agent, số giải pháp sẵn sàng, hoặc nhãn báo lỗi đỏ nếu thực thi thất bại).
  3. Xây dựng `IssueActionSheet` khi bấm vào một vấn đề:
     - Hiển thị tóm tắt + nút bung xem nội dung gốc (`raw_content`).
     - Hiển thị các thẻ giải pháp `A`, `B`, `C`; khi chọn 1 giải pháp, hiện ngay ô `TextField` chứa `draft_text` (cho phép sửa trực tiếp) hoặc khối `A2UI` + nút **[Thực thi & Xóa]**.
     - Thanh `Undo 3 giây` ở đáy màn hình trước khi gửi lệnh `execute` lên server; tự khôi phục thẻ lên đầu danh sách nếu server trả về lỗi.
  4. Nút **[Đi sâu cùng Agent]** (`Cách 2B`): Mở thẳng sang màn hình Chat chuẩn của MotherBase gắn với `issue_id`, kèm thanh banner cố định trên đầu khung chat có nút **[Hoàn tất & Xóa vấn đề]**.

#### Pha 3: Đóng gói Release `v0.2.0` qua OTA & Viết Template Tạo Channel Plugin Mới
- **Mục tiêu**: Phát hành bản `v0.2.0` lên GitHub Releases để điện thoại của bạn tự báo cập nhật OTA, đồng thời tạo tài liệu + template `inbox-channels/_template/` để sau này cắm thêm các MCP khác trong 5 phút.
