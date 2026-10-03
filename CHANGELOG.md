# Changelog

## 1.0.10 (versionCode 11)

Sửa theo review model v1.0.9:

- **Model lọc sạch:** loại ~34k token không phải âm tiết tiếng Việt (tiếng
  Anh, tên Latin, mã web — `of`, `the`, `html`…) khỏi unigram/bigram/
  trigram. Vocab còn ~6.000 âm tiết VN; model nén còn ~1.4 MB.
- **Gợi ý đầu câu kiểu chat:** danh sách mở câu ưu tiên `xin/chào/dạ/tôi/
  anh/em…` trước tần suất Wikipedia (vốn thiên văn phong bài báo).
- **Không sửa từ không-VN:** `correction()` bỏ qua mọi từ không có dạng âm
  tiết tiếng Việt (wiki, url, pour…) — chỉ còn đụng typo gõ thừa phím.
  Tỷ lệ sửa nhầm trên eval: 1.3% → 0.08%.
- **CI chạy test:** workflow thêm `testDebugUnitTest` trước khi build APK.
- **Lỗi nhỏ:** ghi learned_data.tmp đồng bộ (chống ghi chéo 2 luồng); cờ
  "xóa dữ liệu học" tiêu thụ ngay khi focus ô nhập; sửa chú thích
  TextContext.
- Số liệu eval mới (9.373 câu Wikipedia giữ riêng): nextTop3 33.8%,
  tiết kiệm phím 18.6%, sửa nhầm 0.08%, heap model ~4 MB.

## 1.0.9 (versionCode 10)

Bản tổng hợp 4 giai đoạn cải tiến — mỗi giai đoạn là một bước riêng, có thể
revert theo commit.

### Giai đoạn 0 — đo nền
- Thêm benchmark JVM (`EngineBenchmarkTest`), test vòng khứ hồi Telex trên
  toàn từ điển, test xác nhận lỗi gõ ô mật khẩu.
- Log thời gian xử lý phím (chỉ bản debug) + `docs/manual-test.md` kịch bản
  thử tay.

### Giai đoạn 1 — tốc độ
- Chỉ mục gợi ý cập nhật tăng dần: không còn dựng lại sau mỗi từ đã gõ
  (trước: ~5–25 ms khựng mỗi từ; sau: 0 lần dựng lại, vòng 500 từ ~14 ms).
- Dựng chỉ mục từ điển ở luồng nền; không inflate lại layout khi đổi ô nhập;
  cache ngữ cảnh trong lúc gõ; cache cài đặt phím; gom thao tác nhiều lệnh
  vào batch edit.

### Giai đoạn 2 — đúng đắn và an toàn
- Ô mật khẩu/email/URL/no-suggestions: tắt hoàn toàn Telex, gợi ý, tự sửa,
  tự học — ký tự gõ giữ nguyên (`pass` không còn thành `pas`).
- Ngữ cảnh đúng sau dấu câu: sau `. ! ?` hoặc xuống dòng coi như đầu câu,
  không lấy từ cuối câu trước làm ngữ cảnh.
- Tự học chỉ nhận từ toàn chữ cái ≤24 ký tự; từ mới chỉ lên gợi ý sau khi
  gõ ≥2 lần.
- Chuẩn hoá 41 mục từ điển đặt dấu kiểu cũ sang kiểu mới; dọn khóa trùng
  trong mô hình viết tay; `allowBackup=false`.

### Giai đoạn 3 — gợi ý chính xác
- Mô hình ngôn ngữ từ Wikipedia tiếng Việt (CC BY-SA): 40k unigram, ~80k
  bigram, ~248k trigram + tần suất từ đầu câu — thay điểm tần suất phẳng.
- Đoán từ kế tiếp theo backoff trigram → bigram → unigram; gợi ý đầu câu
  riêng; gợi ý hoàn thành xếp theo điểm ngữ cảnh + tần suất + cá nhân.
- Tự sửa bảo thủ: chỉ sửa khi có bằng chứng lỗi gõ (phím kề/sai dấu) và điểm
  mô hình thắng rõ; không đụng từ <3 ký tự, từ có số/ký tự lạ, từ đang là
  tiền tố hợp lệ.
- Dữ liệu học lưu bền trong bộ nhớ app (file có version, ghi atomic, có
  giới hạn, nút "Xóa dữ liệu học" trong app).
- Đánh giá offline trên 9.373 câu giữ riêng: đoán từ kế đúng top-3 từ 1.7%
  lên 37.6%; tiết kiệm ~22.5% số phím; tỷ lệ sửa nhầm 0.3%; model chiếm
  ~2.2 MB trong APK, ~12 MB heap.

### Việc còn lại
- Thử trên máy thật theo `docs/manual-test.md` trước khi phát hành rộng.
