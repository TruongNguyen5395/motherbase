#!/usr/bin/env node
/**
 * Upload 3TSolution project timeline data to Lark Bitable
 * Bitable: "Timeline" (YFc2bPi9gatm93sHETWjQLFGpkh)
 * Tables: Project (tblYg0HGWbDTJgQ9), Công Việc (tblROtRx9eAoVal4)
 */

const https = require('https');

const APP_ID = 'cli_a9f7ea6ea7389e1b';
const APP_SECRET = 'rHowRUFFK3m3zEbPdEwFIezYGCVfGMnS';
const APP_TOKEN = 'YFc2bPi9gatm93sHETWjQLFGpkh';
const TABLE_PROJECT = 'tblYg0HGWbDTJgQ9';
const TABLE_TASK = 'tblROtRx9eAoVal4';

// --- Helpers ---

function apiRequest(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const options = {
      hostname: 'open.larksuite.com',
      path,
      method,
      headers: {
        'Authorization': 'Bearer ' + token,
        'Content-Type': 'application/json',
      },
    };
    if (data) options.headers['Content-Length'] = Buffer.byteLength(data);
    const req = https.request(options, res => {
      let buf = '';
      res.on('data', c => buf += c);
      res.on('end', () => {
        try { resolve(JSON.parse(buf)); } catch (e) { reject(new Error(buf)); }
      });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

/** Convert dd/MM/yyyy to Unix ms (midnight UTC+7) */
function toTimestamp(dateStr) {
  if (!dateStr) return null;
  const [d, m, y] = dateStr.split('/');
  // midnight UTC+7 = (yyyy-MM-ddT00:00:00+07:00)
  const dt = new Date(`${y}-${m.padStart(2,'0')}-${d.padStart(2,'0')}T00:00:00+07:00`);
  return dt.getTime();
}

let token = '';

// --- Data definitions ---

const PROJECTS = ['MES-FV', 'Mobile App MES-FV', 'Triển khai Nhà máy Gỗ'];

// OLD record IDs to delete
const OLD_PROJECT_IDS = ['recvb4IaGAvrbq', 'recvb4IaGA4Mnx', 'recvb4IaGA5sGN', 'recvb4IaGA3VEP', 'recvb4IaGAJgbb'];
const OLD_TASK_IDS = [
  'recdZrjyfv', 'recFtrLfCc', 'reczmxe7zf', 'recZbayogi',
  'recv8nuU8My5Kn', 'recv8nuVLIyLtc', 'recv8nv60nmwnw', 'recv8nv6nuD1qP',
  'recv8nv6sptoFn', 'recv8nv6DG6ZPF', 'recv8nvcX312G3', 'recv8nvee2YEGV',
  'recv8nveln10LN', 'recv8nveqoDjNb', 'recv8nveIm2rUa', 'recv8nveZBq9Sf',
  'recv8nvfkzK12A', 'recv8nvfzzdDzk', 'recv8nvJSt5xfb', 'recv8nvKaEFcAr',
  'recv8nvKrw35UX', 'recv8nvKMpedWk', 'recv8nwgCLWxPj', 'recv8nwhI1bwEu',
  'recv8nwlLGAYlX'
];

// Parent tasks - keyed by a local ID for child linking
// Format: { key, name, deadline (dd/MM/yyyy or null), done (bool) }

const PARENTS_MES = [
  { key: 'mes_1',  name: 'QUY TRÌNH GIAO VIỆC & KIỂM SOÁT TẠI XƯỞNG', deadline: null, done: false },
  { key: 'mes_2',  name: 'BẢNG ĐIỀU HÀNH ĐÁNH GIÁ', deadline: null, done: true },
  { key: 'mes_3',  name: 'BẢNG ĐIỀU HÀNH GIÁM SÁT', deadline: null, done: true },
  { key: 'mes_4',  name: 'HỆ THỐNG BÁO CÁO', deadline: null, done: false },
  { key: 'mes_5',  name: 'KHO BÁN THÀNH PHẨM', deadline: null, done: false },
  { key: 'mes_6',  name: 'QUẢN TRỊ RỦI RO', deadline: null, done: false },
  { key: 'mes_7',  name: 'KHẢO SÁT VÀ HOÀN THIỆN QUY TRÌNH NHÀ MÁY CƠ KHÍ', deadline: null, done: true },
  { key: 'mes_8',  name: 'LỚP KẾ HOẠCH', deadline: '07/04/2026', done: false },
  { key: 'mes_9',  name: 'LỚP ĐIỀU PHỐI', deadline: '03/04/2026', done: false },
  { key: 'mes_10', name: 'LỚP THỰC THI', deadline: '03/04/2026', done: false },
  { key: 'mes_11', name: 'KHO BÁN THÀNH PHẨM (GĐ bổ sung)', deadline: '03/04/2026', done: false },
  { key: 'mes_12', name: 'HỆ THỐNG BÁO CÁO (GĐ bổ sung)', deadline: '03/04/2026', done: false },
];

const PARENTS_MOBILE = [
  { key: 'mob_1', name: 'BẢNG ĐIỀU HÀNH ĐÁNH GIÁ', deadline: '10/03/2026', done: false },
  { key: 'mob_2', name: 'BẢNG ĐIỀU HÀNH GIÁM SÁT', deadline: '16/03/2026', done: false },
  { key: 'mob_3', name: 'CÁC CHỨC NĂNG HỖ TRỢ XEM THÔNG TIN', deadline: '16/03/2026', done: false },
  { key: 'mob_4', name: 'QUY TRÌNH GIAO VIỆC & KIỂM SOÁT TẠI XƯỞNG', deadline: null, done: false },
];

const PARENTS_WOOD = [
  { key: 'wood_1', name: 'Thu thập thông tin & chốt phương án', deadline: '16/03/2026', done: false },
  { key: 'wood_2', name: 'Cấu hình phần mềm & chuẩn bị', deadline: '30/03/2026', done: false },
  { key: 'wood_3', name: 'Thiết lập BOM sản xuất & Database', deadline: '15/04/2026', done: false },
  { key: 'wood_4', name: 'Training sử dụng phần mềm', deadline: '15/04/2026', done: false },
  { key: 'wood_5', name: 'Chạy thử (Pilot)', deadline: '29/04/2026', done: false },
  { key: 'wood_6', name: 'Đánh giá & hiệu chỉnh', deadline: '29/05/2026', done: false },
  { key: 'wood_7', name: 'Chạy chính thức', deadline: '01/06/2026', done: false },
  { key: 'wood_8', name: 'Bàn giao & nghiệm thu', deadline: '30/06/2026', done: false },
];

const ALL_PARENTS = [...PARENTS_MES, ...PARENTS_MOBILE, ...PARENTS_WOOD];

// Child tasks: { parentKey, name, deadline, done }

const CHILDREN_MES = [
  // Section 1
  { parentKey: 'mes_1', name: 'Quy trình giao phiếu nhiệm vụ cho công nhân', deadline: null, done: false },
  { parentKey: 'mes_1', name: 'Quy trình công nhân nhận phiếu nhiệm vụ, thực hiện và đóng phiếu nhiệm vụ', deadline: null, done: false },
  // Section 2
  { parentKey: 'mes_2', name: 'Tỉ lệ số phiếu nhiệm vụ được đóng trên tổng số phiếu được giao', deadline: null, done: true },
  { parentKey: 'mes_2', name: 'Tỉ lệ số phiếu nhiệm vụ hoàn thành đúng hạn', deadline: null, done: true },
  { parentKey: 'mes_2', name: 'Tỉ lệ số lệnh sản xuất hoàn thành đúng kế hoạch trên tổng số lệnh được giao trong kỳ sản xuất', deadline: null, done: true },
  { parentKey: 'mes_2', name: 'Tỉ lệ giữa thời gian triển khai Lệnh sản xuất thực tế so với thời gian dự tính', deadline: null, done: true },
  // Section 3
  { parentKey: 'mes_3', name: 'Số Phiếu nhiệm vụ đang mở', deadline: null, done: true },
  { parentKey: 'mes_3', name: 'Số Phiếu chưa đóng khi hết ca', deadline: null, done: true },
  { parentKey: 'mes_3', name: 'Số Phiếu nhiệm vụ tồn đọng (trễ hạn)', deadline: null, done: true },
  { parentKey: 'mes_3', name: 'Tiến độ công việc trong ngày', deadline: null, done: true },
  { parentKey: 'mes_3', name: 'Tiến độ công việc xử lý theo xưởng', deadline: null, done: true },
  { parentKey: 'mes_3', name: 'Số Lệnh sản xuất đang chạy', deadline: null, done: true },
  { parentKey: 'mes_3', name: 'Số ngày đã thực hiện Lệnh sản xuất', deadline: null, done: true },
  // Section 4
  { parentKey: 'mes_4', name: 'Báo cáo Tổng hợp sản xuất', deadline: null, done: true },
  { parentKey: 'mes_4', name: 'Báo cáo Tiến độ theo Lệnh sản xuất', deadline: null, done: true },
  { parentKey: 'mes_4', name: 'Báo cáo Thực hiện Phiếu nhiệm vụ', deadline: '11/02/2026', done: true },
  { parentKey: 'mes_4', name: 'Báo cáo bàn giao bán thành phẩm giữa các xưởng', deadline: null, done: false },
  // Section 5
  { parentKey: 'mes_5', name: 'Quy trình bàn giao giữa các xưởng', deadline: null, done: false },
  { parentKey: 'mes_5', name: 'Phiếu xuất kho bán thành phẩm tại xưởng cuối', deadline: null, done: false },
  // Section 6
  { parentKey: 'mes_6', name: 'Triển khai hệ thống mạng 4G phòng trường hợp mất mạng tại xưởng', deadline: '02/03/2026', done: true },
  // Section 7
  { parentKey: 'mes_7', name: 'Khảo sát & Thu thập thông tin xây dựng quy trình nhà máy cơ khí', deadline: null, done: true },
  { parentKey: 'mes_7', name: 'Báo cáo quá trình khảo sát thông tin', deadline: null, done: true },
  // Section 8 - LỚP KẾ HOẠCH
  { parentKey: 'mes_8', name: 'Tự động tạo Công việc (Task) từ Đơn hàng', deadline: '07/04/2026', done: false },
  { parentKey: 'mes_8', name: 'Tính toán Ngày bắt đầu/Kết thúc (LSD/LFD) cho danh sách công việc', deadline: '07/04/2026', done: false },
  { parentKey: 'mes_8', name: 'Kiểm soát Tồn kho Bán thành phẩm & Chặn Công việc thiếu vật tư', deadline: '07/04/2026', done: false },
  { parentKey: 'mes_8', name: 'Dashboard Kế hoạch Tổng thể (Master Plan)', deadline: '21/04/2026', done: false },
  // Section 9 - LỚP ĐIỀU PHỐI
  { parentKey: 'mes_9', name: 'Chức năng quản lý danh sách công việc cho xưởng trưởng', deadline: '03/04/2026', done: false },
  { parentKey: 'mes_9', name: 'Chức năng tạo phiếu nhiệm vụ (max 4h) từ danh sách công việc', deadline: '03/04/2026', done: false },
  { parentKey: 'mes_9', name: 'Chức năng kiểm soát phiếu nhiệm vụ (xưởng trưởng cập nhật số lượng, đóng, hủy, xác nhận, ghi chú)', deadline: '03/04/2026', done: false },
  // Section 10 - LỚP THỰC THI
  { parentKey: 'mes_10', name: 'Chức năng cập nhật phiếu nhiệm vụ (công nhân cập nhật số lượng, đóng phiếu)', deadline: '03/04/2026', done: false },
  { parentKey: 'mes_10', name: 'Chức năng đồng bộ tiến độ công việc khi nhiệm vụ hoàn thành', deadline: '03/04/2026', done: false },
  // Section 11 - KHO BÁN THÀNH PHẨM (GĐ bổ sung)
  { parentKey: 'mes_11', name: 'Quy trình bàn giao giữa các xưởng', deadline: '03/04/2026', done: false },
  { parentKey: 'mes_11', name: 'Phiếu xuất kho bán thành phẩm tại xưởng cuối', deadline: '03/04/2026', done: false },
  // Section 12 - HỆ THỐNG BÁO CÁO (GĐ bổ sung)
  { parentKey: 'mes_12', name: 'Báo cáo bàn giao bán thành phẩm giữa các xưởng', deadline: '03/04/2026', done: false },
];

const CHILDREN_MOBILE = [
  // Section 1 - BẢNG ĐIỀU HÀNH ĐÁNH GIÁ
  { parentKey: 'mob_1', name: 'Tỉ lệ số phiếu nhiệm vụ được đóng trên tổng số phiếu được giao', deadline: '10/03/2026', done: false },
  { parentKey: 'mob_1', name: 'Tỉ lệ số phiếu nhiệm vụ hoàn thành đúng hạn', deadline: '10/03/2026', done: false },
  { parentKey: 'mob_1', name: 'Tỉ lệ số lệnh sản xuất hoàn thành đúng kế hoạch trên tổng số lệnh được giao trong kỳ sản xuất', deadline: '10/03/2026', done: false },
  { parentKey: 'mob_1', name: 'Tỉ lệ giữa thời gian triển khai Lệnh sản xuất thực tế so với thời gian dự tính', deadline: '10/03/2026', done: false },
  // Section 2 - BẢNG ĐIỀU HÀNH GIÁM SÁT
  { parentKey: 'mob_2', name: 'Biểu đồ số giờ làm việc tích lũy theo từng khung giờ trên tổng số giờ làm việc được giao trong ngày', deadline: '16/03/2026', done: false },
  { parentKey: 'mob_2', name: 'Số Phiếu nhiệm vụ đang mở', deadline: '16/03/2026', done: false },
  { parentKey: 'mob_2', name: 'Số Phiếu chưa đóng khi hết ca', deadline: '16/03/2026', done: false },
  { parentKey: 'mob_2', name: 'Số Phiếu nhiệm vụ tồn đọng (trễ hạn)', deadline: '16/03/2026', done: false },
  { parentKey: 'mob_2', name: 'Biểu đồ công việc được xử lý tính theo từng giờ', deadline: '16/03/2026', done: false },
  { parentKey: 'mob_2', name: 'Biểu đồ xác định điểm nghẽn công việc theo từng xưởng', deadline: '16/03/2026', done: false },
  { parentKey: 'mob_2', name: 'Số Lệnh sản xuất đang chạy', deadline: '16/03/2026', done: false },
  { parentKey: 'mob_2', name: 'Số ngày đã thực hiện Lệnh sản xuất', deadline: '16/03/2026', done: false },
  // Section 3 - CÁC CHỨC NĂNG HỖ TRỢ XEM THÔNG TIN
  { parentKey: 'mob_3', name: 'Xem được danh sách & thông tin Lệnh Sản Xuất', deadline: '16/03/2026', done: false },
  { parentKey: 'mob_3', name: 'Xem được danh sách & thông tin BOM sản xuất', deadline: '16/03/2026', done: false },
  // Section 4 - QUY TRÌNH GIAO VIỆC & KIỂM SOÁT TẠI XƯỞNG
  { parentKey: 'mob_4', name: 'Chức năng sử dụng app điện thoại để tạo nhiệm vụ cho công nhân từ danh sách công việc', deadline: '10/04/2026', done: false },
  { parentKey: 'mob_4', name: 'Chức năng sử dụng app điện thoại để cập nhật phiếu nhiệm vụ hoàn thành của công nhân', deadline: '10/04/2026', done: false },
  { parentKey: 'mob_4', name: 'Chức năng quản lý danh sách công việc', deadline: '10/04/2026', done: false },
  { parentKey: 'mob_4', name: 'Chức năng sử dụng app điện thoại để tạo phiếu luân chuyển bán thành phẩm giữa các xưởng', deadline: '17/04/2026', done: false },
  { parentKey: 'mob_4', name: 'Chức năng sử dụng app điện thoại để xác nhận phiếu chuyển giao bán thành phẩm giữa các xưởng', deadline: '17/04/2026', done: false },
];

const CHILDREN_WOOD = [
  // Section 1 - Thu thập thông tin & chốt phương án
  { parentKey: 'wood_1', name: 'Lập sơ đồ mạng thực tế hiện trạng của nhà máy', deadline: '15/02/2026', done: false },
  { parentKey: 'wood_1', name: 'Lập sơ đồ Bố trí máy và các vị trí làm việc của 3 khu xưởng nhà máy gỗ', deadline: '20/02/2026', done: false },
  { parentKey: 'wood_1', name: 'Viết mô tả công việc của nhà máy Gỗ', deadline: null, done: true },
  { parentKey: 'wood_1', name: 'Khảo sát thực tế theo 02 Sản phẩm và lập danh sách các công đoạn / xưởng / máy', deadline: null, done: true },
  { parentKey: 'wood_1', name: 'Vẽ lưu đồ sản xuất của nhà máy gỗ', deadline: '06/03/2026', done: false },
  { parentKey: 'wood_1', name: 'Lập sơ đồ bố trí bảng điều khiển (PC, hạ tầng mạng,...)', deadline: '06/03/2026', done: false },
  { parentKey: 'wood_1', name: 'Xác định phương án tạo & đóng phiếu nhiệm vụ', deadline: '06/03/2026', done: false },
  { parentKey: 'wood_1', name: 'Xác định phương án bàn giao bán thành phẩm', deadline: '06/03/2026', done: false },
  { parentKey: 'wood_1', name: 'Họp Kick-Off dự án với BGĐ và thảo luận phương án triển khai', deadline: '13/03/2026', done: false },
  { parentKey: 'wood_1', name: 'Thảo luận phương án với xưởng trưởng, quản đốc để ghi nhận ý kiến đóng góp', deadline: '16/03/2026', done: false },
  // Section 2 - Cấu hình phần mềm & chuẩn bị
  { parentKey: 'wood_2', name: 'Cấu hình danh sách công đoạn của nhà máy Gỗ', deadline: '15/03/2026', done: false },
  { parentKey: 'wood_2', name: 'Cấu hình metadata cho nhà máy Gỗ', deadline: '15/03/2026', done: false },
  { parentKey: 'wood_2', name: 'Cấu hình quy tắc tạo / đóng Phiếu nhiệm vụ', deadline: '15/03/2026', done: false },
  { parentKey: 'wood_2', name: 'Kiểm tra thiết bị phần cứng tại xưởng (PC & đầu đọc thẻ tại xưởng)', deadline: '30/03/2026', done: false },
  { parentKey: 'wood_2', name: 'Tạo tài khoản & phân quyền người dùng', deadline: '30/03/2026', done: false },
  // Section 3 - Thiết lập BOM sản xuất & Database
  { parentKey: 'wood_3', name: 'Rà soát BOM sản xuất hiện tại đưa ra báo cáo đánh giá sơ bộ về tính khả thi và các điều chỉnh nếu có', deadline: '10/03/2026', done: false },
  { parentKey: 'wood_3', name: 'Thảo luận BOM với BGĐ / xưởng trưởng / kỹ thuật', deadline: '10/03/2026', done: false },
  { parentKey: 'wood_3', name: 'Lập danh sách BOM mới phù hợp thực tế sản xuất', deadline: '10/03/2026', done: false },
  { parentKey: 'wood_3', name: 'Đo thời gian sản xuất thực tế để cập nhật BOM sản xuất', deadline: '01/04/2026', done: false },
  { parentKey: 'wood_3', name: 'Nhập BOM sản xuất của nhà máy Gỗ vào hệ thống FV', deadline: '15/04/2026', done: false },
  // Section 4 - Training sử dụng phần mềm
  { parentKey: 'wood_4', name: 'Hướng dẫn vận hành cho xưởng trưởng (Giao phiếu, sử dụng bảng điều khiển giám sát, thực hiện bàn giao bán thành phẩm)', deadline: '15/04/2026', done: false },
  { parentKey: 'wood_4', name: 'Hướng dẫn vận hành cho công nhân (Nhận và đóng phiếu nhiệm vụ)', deadline: '15/04/2026', done: false },
  { parentKey: 'wood_4', name: 'Hướng dẫn điều hành cho Quản Đốc và BGĐ (Bảng điều hành giám sát, đánh giá và Báo Cáo)', deadline: '15/04/2026', done: false },
  // Section 5 - Chạy thử (Pilot)
  { parentKey: 'wood_5', name: 'Theo dõi việc tạo Phiếu nhiệm vụ của xưởng trưởng', deadline: '29/04/2026', done: false },
  { parentKey: 'wood_5', name: 'Theo dõi việc quét thẻ & đóng Phiếu của công nhân', deadline: '29/04/2026', done: false },
  { parentKey: 'wood_5', name: 'Ghi nhận lỗi, vướng mắc phát sinh', deadline: '29/04/2026', done: false },
  { parentKey: 'wood_5', name: 'Báo cáo tổng hợp dữ liệu chạy thử & đánh giá', deadline: '29/04/2026', done: false },
  // Section 6 - Đánh giá & hiệu chỉnh
  { parentKey: 'wood_6', name: 'Đánh giá luồng Phiếu nhiệm vụ', deadline: '08/05/2026', done: false },
  { parentKey: 'wood_6', name: 'Đánh giá BOM & thời gian chuẩn', deadline: '08/05/2026', done: false },
  { parentKey: 'wood_6', name: 'Điều chỉnh BOM sản xuất / quy tắc hoặc trường thông tin Phiếu Nhiệm Vụ (nếu cần)', deadline: '29/05/2026', done: false },
  { parentKey: 'wood_6', name: 'Báo cáo tổng hợp thay đổi và xác nhận hệ thống đủ khả năng Go-Live', deadline: '29/05/2026', done: false },
  // Section 7 - Chạy chính thức
  { parentKey: 'wood_7', name: 'Họp thông báo thời điểm chạy chính thức với BGĐ', deadline: '01/06/2026', done: false },
  { parentKey: 'wood_7', name: 'Báo cáo Ban giám đốc giai đoạn đầu vận hành', deadline: '01/06/2026', done: false },
  { parentKey: 'wood_7', name: 'Ngừng theo dõi thủ công song song', deadline: '01/06/2026', done: false },
  // Section 8 - Bàn giao & nghiệm thu
  { parentKey: 'wood_8', name: 'Bàn giao tài liệu hướng dẫn sử dụng cho nhà máy', deadline: '30/06/2026', done: false },
  { parentKey: 'wood_8', name: 'Lập hồ sơ nghiệm thu với Ban Giám đốc', deadline: '30/06/2026', done: false },
];

const ALL_CHILDREN = [...CHILDREN_MES, ...CHILDREN_MOBILE, ...CHILDREN_WOOD];

// --- Main ---

async function main() {
  console.log('=== Lark Bitable Timeline Upload ===\n');

  // Step 0: Get token
  console.log('[Step 0] Getting API token...');
  const tokenRes = await new Promise((resolve, reject) => {
    const data = JSON.stringify({ app_id: APP_ID, app_secret: APP_SECRET });
    const options = {
      hostname: 'open.larksuite.com',
      path: '/open-apis/auth/v3/tenant_access_token/internal',
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) },
    };
    const req = https.request(options, res => {
      let buf = '';
      res.on('data', c => buf += c);
      res.on('end', () => resolve(JSON.parse(buf)));
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
  if (tokenRes.code !== 0) throw new Error('Token failed: ' + JSON.stringify(tokenRes));
  token = tokenRes.tenant_access_token;
  console.log('  Token acquired.\n');

  // Step 1: Delete old records
  console.log('[Step 1] Deleting old records...');
  const delTasks = await apiRequest('POST',
    `/open-apis/bitable/v1/apps/${APP_TOKEN}/tables/${TABLE_TASK}/records/batch_delete`,
    { records: OLD_TASK_IDS });
  console.log('  Tasks deleted:', delTasks.code === 0 ? 'OK' : JSON.stringify(delTasks));

  const delProjects = await apiRequest('POST',
    `/open-apis/bitable/v1/apps/${APP_TOKEN}/tables/${TABLE_PROJECT}/records/batch_delete`,
    { records: OLD_PROJECT_IDS });
  console.log('  Projects deleted:', delProjects.code === 0 ? 'OK' : JSON.stringify(delProjects));
  console.log();

  // Step 2: Create projects
  console.log('[Step 2] Creating 3 projects...');
  const projRes = await apiRequest('POST',
    `/open-apis/bitable/v1/apps/${APP_TOKEN}/tables/${TABLE_PROJECT}/records/batch_create`,
    { records: PROJECTS.map(p => ({ fields: { 'Text': p } })) });
  if (projRes.code !== 0) throw new Error('Project creation failed: ' + JSON.stringify(projRes));
  console.log('  Created:', projRes.data.records.map(r => r.fields['Text']).join(', '));
  console.log();

  // Step 3: Create parent tasks
  console.log(`[Step 3] Creating ${ALL_PARENTS.length} parent tasks...`);
  const parentRecords = ALL_PARENTS.map(p => {
    const fields = { 'Tên Công Việc': p.name };
    if (p.deadline) fields['Deadline'] = toTimestamp(p.deadline);
    if (p.done) fields['Hoàn Thành?'] = true;
    return { fields };
  });

  const parentRes = await apiRequest('POST',
    `/open-apis/bitable/v1/apps/${APP_TOKEN}/tables/${TABLE_TASK}/records/batch_create`,
    { records: parentRecords });
  if (parentRes.code !== 0) throw new Error('Parent creation failed: ' + JSON.stringify(parentRes));

  // Build key→recordId map
  const keyToRecordId = {};
  parentRes.data.records.forEach((r, i) => {
    keyToRecordId[ALL_PARENTS[i].key] = r.record_id;
  });
  console.log('  Created parent tasks. Record ID map:');
  Object.entries(keyToRecordId).forEach(([k, v]) => console.log(`    ${k} → ${v}`));
  console.log();

  // Step 4: Create child tasks (in batches of 100 for safety)
  console.log(`[Step 4] Creating ${ALL_CHILDREN.length} child tasks...`);
  const childRecords = ALL_CHILDREN.map(c => {
    const parentRecordId = keyToRecordId[c.parentKey];
    const fields = {
      'Tên Công Việc': c.name,
      'Parent items': [parentRecordId],
    };
    if (c.deadline) fields['Deadline'] = toTimestamp(c.deadline);
    if (c.done) fields['Hoàn Thành?'] = true;
    return { fields };
  });

  // Batch in chunks of 100
  const BATCH_SIZE = 100;
  let totalCreated = 0;
  for (let i = 0; i < childRecords.length; i += BATCH_SIZE) {
    const batch = childRecords.slice(i, i + BATCH_SIZE);
    const childRes = await apiRequest('POST',
      `/open-apis/bitable/v1/apps/${APP_TOKEN}/tables/${TABLE_TASK}/records/batch_create`,
      { records: batch });
    if (childRes.code !== 0) {
      console.error(`  Batch ${i}-${i+batch.length} FAILED:`, JSON.stringify(childRes));
    } else {
      totalCreated += childRes.data.records.length;
      console.log(`  Batch ${i+1}-${i+batch.length}: OK (${childRes.data.records.length} created)`);
    }
  }
  console.log(`  Total children created: ${totalCreated}\n`);

  // Summary
  console.log('=== DONE ===');
  console.log(`Projects: 3`);
  console.log(`Parent tasks: ${ALL_PARENTS.length}`);
  console.log(`Child tasks: ${totalCreated}`);
  console.log(`Total records: ${3 + ALL_PARENTS.length + totalCreated}`);
}

main().catch(err => {
  console.error('FATAL:', err.message);
  process.exit(1);
});
