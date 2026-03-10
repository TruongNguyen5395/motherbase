#!/usr/bin/env node
/**
 * Fix MES-FV & Mobile App tasks: split into 4-phase children
 * [Demo VP], [Pilot NM], [Sửa Lỗi], [Go-live]
 */

const https = require('https');

const APP_ID = 'cli_a9f7ea6ea7389e1b';
const APP_SECRET = 'rHowRUFFK3m3zEbPdEwFIezYGCVfGMnS';
const APP_TOKEN = 'YFc2bPi9gatm93sHETWjQLFGpkh';
const TABLE_TASK = 'tblROtRx9eAoVal4';

const PROJ_MES = 'recvdsg8wZj8Iy';
const PROJ_MOBILE = 'recvdsg8wZkVyo';

// Parent record IDs
const MES_BASIC_PARENTS = [
  'recvdsg8SoLDKE', 'recvdsg8Solmph', 'recvdsg8SorjfN', 'recvdsg8SoHDTy',
  'recvdsg8SoZ8Qf', 'recvdsg8SopInp', 'recvdsg8Soiisv'
];
const MES_SUPP_PARENTS = {
  'recvdsg8SoSxo7': 'mes_8',
  'recvdsg8SojiSe': 'mes_9',
  'recvdsg8So7L6U': 'mes_10',
  'recvdsg8SotOg4': 'mes_11',
  'recvdsg8SoiGP3': 'mes_12',
};
const MOBILE_PARENTS = {
  'recvdsg8SoYieQ': 'mob_1',
  'recvdsg8So5TeY': 'mob_2',
  'recvdsg8SoRhCq': 'mob_3',
  'recvdsg8SoVwM1': 'mob_4',
};

let token = '';

function apiRequest(method, path, body) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const options = {
      hostname: 'open.larksuite.com', path, method,
      headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
    };
    if (data) options.headers['Content-Length'] = Buffer.byteLength(data);
    const req = https.request(options, res => {
      let buf = '';
      res.on('data', c => buf += c);
      res.on('end', () => { try { resolve(JSON.parse(buf)); } catch (e) { reject(new Error(buf)); } });
    });
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

function toTimestamp(dateStr) {
  if (!dateStr) return null;
  const [d, m, y] = dateStr.split('/');
  return new Date(`${y}-${m.padStart(2,'0')}-${d.padStart(2,'0')}T00:00:00+07:00`).getTime();
}

const PHASES = ['Demo VP', 'Pilot NM', 'Sửa Lỗi', 'Go-live'];

// --- Phase date definitions ---

// MES-FV Supplementary
const MES_SECTION_DATES = {
  // Section 8 default dates (for 8.1, 8.2, 8.3)
  '8': ['16/03/2026', '19/03/2026', '30/04/2026', '07/04/2026'],
  // Section 8.4 has own dates
  '8.4': ['01/04/2026', '04/04/2026', '14/04/2026', '21/04/2026'],
  // Sections 9-12 share same dates
  '9': ['20/03/2026', '23/03/2026', '03/04/2026', '03/04/2026'],
  '10': ['20/03/2026', '23/03/2026', '03/04/2026', '03/04/2026'],
  '11': ['20/03/2026', '23/03/2026', '03/04/2026', '03/04/2026'],
  '12': ['20/03/2026', '23/03/2026', '03/04/2026', '03/04/2026'],
};

const MES_SUPP_CHILDREN = [
  { parentId: 'recvdsg8SoSxo7', name: 'Tự động tạo Công việc (Task) từ Đơn hàng', dates: '8' },
  { parentId: 'recvdsg8SoSxo7', name: 'Tính toán Ngày bắt đầu/Kết thúc (LSD/LFD) cho danh sách công việc', dates: '8' },
  { parentId: 'recvdsg8SoSxo7', name: 'Kiểm soát Tồn kho Bán thành phẩm & Chặn Công việc thiếu vật tư', dates: '8' },
  { parentId: 'recvdsg8SoSxo7', name: 'Dashboard Kế hoạch Tổng thể (Master Plan)', dates: '8.4' },
  { parentId: 'recvdsg8SojiSe', name: 'Chức năng quản lý danh sách công việc cho xưởng trưởng', dates: '9' },
  { parentId: 'recvdsg8SojiSe', name: 'Chức năng tạo phiếu nhiệm vụ (max 4h) từ danh sách công việc', dates: '9' },
  { parentId: 'recvdsg8SojiSe', name: 'Chức năng kiểm soát phiếu nhiệm vụ (xưởng trưởng cập nhật số lượng, đóng, hủy, xác nhận, ghi chú)', dates: '9' },
  { parentId: 'recvdsg8So7L6U', name: 'Chức năng cập nhật phiếu nhiệm vụ (công nhân cập nhật số lượng, đóng phiếu)', dates: '10' },
  { parentId: 'recvdsg8So7L6U', name: 'Chức năng đồng bộ tiến độ công việc khi nhiệm vụ hoàn thành', dates: '10' },
  { parentId: 'recvdsg8SotOg4', name: 'Quy trình bàn giao giữa các xưởng', dates: '11' },
  { parentId: 'recvdsg8SotOg4', name: 'Phiếu xuất kho bán thành phẩm tại xưởng cuối', dates: '11' },
  { parentId: 'recvdsg8SoiGP3', name: 'Báo cáo bàn giao bán thành phẩm giữa các xưởng', dates: '12' },
];

// Mobile App
const MOBILE_SECTION_DATES = {
  '1': ['27/02/2026', '02/03/2026', '09/03/2026', '10/03/2026'],
  '2': ['06/03/2026', '09/03/2026', '13/03/2026', '16/03/2026'],
  '3': ['06/03/2026', '09/03/2026', '13/03/2026', '16/03/2026'],
  '4a': ['20/03/2026', '23/03/2026', '03/04/2026', '10/04/2026'],  // 4.1-4.3
  '4b': ['27/03/2026', '30/03/2026', '10/04/2026', '17/04/2026'],  // 4.4-4.5
};

const MOBILE_CHILDREN = [
  // Section 1
  { parentId: 'recvdsg8SoYieQ', name: 'Tỉ lệ số phiếu nhiệm vụ được đóng trên tổng số phiếu được giao', dates: '1' },
  { parentId: 'recvdsg8SoYieQ', name: 'Tỉ lệ số phiếu nhiệm vụ hoàn thành đúng hạn', dates: '1' },
  { parentId: 'recvdsg8SoYieQ', name: 'Tỉ lệ số lệnh sản xuất hoàn thành đúng kế hoạch trên tổng số lệnh được giao trong kỳ sản xuất', dates: '1' },
  { parentId: 'recvdsg8SoYieQ', name: 'Tỉ lệ giữa thời gian triển khai Lệnh sản xuất thực tế so với thời gian dự tính', dates: '1' },
  // Section 2
  { parentId: 'recvdsg8So5TeY', name: 'Biểu đồ số giờ làm việc tích lũy theo từng khung giờ trên tổng số giờ làm việc được giao trong ngày', dates: '2' },
  { parentId: 'recvdsg8So5TeY', name: 'Số Phiếu nhiệm vụ đang mở', dates: '2' },
  { parentId: 'recvdsg8So5TeY', name: 'Số Phiếu chưa đóng khi hết ca', dates: '2' },
  { parentId: 'recvdsg8So5TeY', name: 'Số Phiếu nhiệm vụ tồn đọng (trễ hạn)', dates: '2' },
  { parentId: 'recvdsg8So5TeY', name: 'Biểu đồ công việc được xử lý tính theo từng giờ', dates: '2' },
  { parentId: 'recvdsg8So5TeY', name: 'Biểu đồ xác định điểm nghẽn công việc theo từng xưởng', dates: '2' },
  { parentId: 'recvdsg8So5TeY', name: 'Số Lệnh sản xuất đang chạy', dates: '2' },
  { parentId: 'recvdsg8So5TeY', name: 'Số ngày đã thực hiện Lệnh sản xuất', dates: '2' },
  // Section 3
  { parentId: 'recvdsg8SoRhCq', name: 'Xem được danh sách & thông tin Lệnh Sản Xuất', dates: '3' },
  { parentId: 'recvdsg8SoRhCq', name: 'Xem được danh sách & thông tin BOM sản xuất', dates: '3' },
  // Section 4
  { parentId: 'recvdsg8SoVwM1', name: 'Chức năng sử dụng app điện thoại để tạo nhiệm vụ cho công nhân từ danh sách công việc', dates: '4a' },
  { parentId: 'recvdsg8SoVwM1', name: 'Chức năng sử dụng app điện thoại để cập nhật phiếu nhiệm vụ hoàn thành của công nhân', dates: '4a' },
  { parentId: 'recvdsg8SoVwM1', name: 'Chức năng quản lý danh sách công việc', dates: '4a' },
  { parentId: 'recvdsg8SoVwM1', name: 'Chức năng sử dụng app điện thoại để tạo phiếu luân chuyển bán thành phẩm giữa các xưởng', dates: '4b' },
  { parentId: 'recvdsg8SoVwM1', name: 'Chức năng sử dụng app điện thoại để xác nhận phiếu chuyển giao bán thành phẩm giữa các xưởng', dates: '4b' },
];

// --- Main ---

async function main() {
  console.log('=== Fix Phase Structure ===\n');

  // Get token
  console.log('[Step 0] Getting token...');
  const tokenRes = await new Promise((resolve, reject) => {
    const data = JSON.stringify({ app_id: APP_ID, app_secret: APP_SECRET });
    const req = https.request({ hostname: 'open.larksuite.com', path: '/open-apis/auth/v3/tenant_access_token/internal', method: 'POST', headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } }, res => { let b=''; res.on('data',c=>b+=c); res.on('end',()=>resolve(JSON.parse(b))); });
    req.write(data); req.end();
  });
  token = tokenRes.tenant_access_token;
  console.log('  OK\n');

  // Fetch all tasks
  console.log('[Fetch] Getting all current tasks...');
  const allRes = await apiRequest('GET', `/open-apis/bitable/v1/apps/${APP_TOKEN}/tables/${TABLE_TASK}/records?page_size=500`);
  const allTasks = allRes.data.items;
  console.log('  Total:', allTasks.length, '\n');

  // Step 1: Archive MES basic parents + their children
  console.log('[Step 1] Archiving MES-FV basic phase...');
  const toArchive = [];
  allTasks.forEach(r => {
    // Is it a basic parent?
    if (MES_BASIC_PARENTS.includes(r.record_id)) {
      toArchive.push(r.record_id);
      return;
    }
    // Is it a child of a basic parent?
    const pi = r.fields['Parent items'];
    if (pi && pi.length > 0 && pi[0].record_ids) {
      const parentId = pi[0].record_ids[0];
      if (MES_BASIC_PARENTS.includes(parentId)) {
        toArchive.push(r.record_id);
      }
    }
  });
  console.log('  Records to archive:', toArchive.length);

  if (toArchive.length > 0) {
    const archiveRecords = toArchive.map(id => ({ record_id: id, fields: { 'Archived': true } }));
    for (let i = 0; i < archiveRecords.length; i += 100) {
      const batch = archiveRecords.slice(i, i + 100);
      const res = await apiRequest('POST',
        `/open-apis/bitable/v1/apps/${APP_TOKEN}/tables/${TABLE_TASK}/records/batch_update`,
        { records: batch });
      console.log('  Archive batch:', res.code === 0 ? 'OK' : JSON.stringify(res).substring(0, 200));
    }
  }
  console.log();

  // Step 2: Identify and delete old MES supp + Mobile children
  console.log('[Step 2] Deleting old single-task children...');
  const mesSuppParentIds = Object.keys(MES_SUPP_PARENTS);
  const mobileParentIds = Object.keys(MOBILE_PARENTS);
  const toDelete = [];

  allTasks.forEach(r => {
    const pi = r.fields['Parent items'];
    if (pi && pi.length > 0 && pi[0].record_ids) {
      const parentId = pi[0].record_ids[0];
      if (mesSuppParentIds.includes(parentId) || mobileParentIds.includes(parentId)) {
        toDelete.push(r.record_id);
      }
    }
  });
  console.log('  Records to delete:', toDelete.length);

  if (toDelete.length > 0) {
    const res = await apiRequest('POST',
      `/open-apis/bitable/v1/apps/${APP_TOKEN}/tables/${TABLE_TASK}/records/batch_delete`,
      { records: toDelete });
    console.log('  Delete:', res.code === 0 ? 'OK' : JSON.stringify(res).substring(0, 200));
  }
  console.log();

  // Step 3: Create new phase children
  console.log('[Step 3] Creating phase children...');

  function buildPhaseRecords(children, sectionDates, projectId) {
    const records = [];
    for (const child of children) {
      const dates = sectionDates[child.dates];
      for (let p = 0; p < 4; p++) {
        const fields = {
          'Tên Công Việc': `[${PHASES[p]}] ${child.name}`,
          'Parent items': [child.parentId],
          'Deadline': toTimestamp(dates[p]),
          'Project': [projectId],
        };
        records.push({ fields });
      }
    }
    return records;
  }

  const mesRecords = buildPhaseRecords(MES_SUPP_CHILDREN, MES_SECTION_DATES, PROJ_MES);
  const mobileRecords = buildPhaseRecords(MOBILE_CHILDREN, MOBILE_SECTION_DATES, PROJ_MOBILE);
  const allNewRecords = [...mesRecords, ...mobileRecords];

  console.log('  MES supplementary phase tasks:', mesRecords.length);
  console.log('  Mobile App phase tasks:', mobileRecords.length);
  console.log('  Total new records:', allNewRecords.length);

  // Batch create in chunks of 100
  let totalCreated = 0;
  for (let i = 0; i < allNewRecords.length; i += 100) {
    const batch = allNewRecords.slice(i, i + 100);
    const res = await apiRequest('POST',
      `/open-apis/bitable/v1/apps/${APP_TOKEN}/tables/${TABLE_TASK}/records/batch_create`,
      { records: batch });
    if (res.code !== 0) {
      console.error('  Batch', i, 'FAILED:', JSON.stringify(res).substring(0, 300));
    } else {
      totalCreated += res.data.records.length;
      console.log('  Batch', i + 1, '-', i + batch.length, ': OK');
    }
  }
  console.log('  Total created:', totalCreated, '\n');

  // Summary
  console.log('=== DONE ===');
  console.log('Archived (MES basic):', toArchive.length);
  console.log('Deleted (old children):', toDelete.length);
  console.log('Created (phase children):', totalCreated);
}

main().catch(err => {
  console.error('FATAL:', err.message);
  process.exit(1);
});
