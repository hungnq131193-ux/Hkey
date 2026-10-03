# Changelog

## 1.3.0 (versionCode 15)

**Gợi ý học theo mức sử dụng — càng gõ càng chuẩn**
- Trigram cá nhân: nhớ cả cụm 3 từ hay gõ ("tối nay" -> "đi ngủ"), xếp
  trên bigram cá nhân nhưng dưới trigram từ điển cho tới khi gõ lặp đủ
  nhiều.
- Ưu tiên mới dùng: từ vừa gõ được cộng điểm, mờ dần sau ~14 ngày không
  dùng lại — thói quen hiện tại thắng thói quen cũ.
- File dữ liệu học lên v2 (lưu thêm trigram, trần 20k cạnh); vẫn đọc được
  dữ liệu đã học từ bản cũ — nâng cấp không mất gì.

**Bàn phím theo thiết bị**
- Màn hình ngang: hàng phím thấp lại ~20% — bàn phím không còn chiếm gần
  nửa máy.
- Tablet / màn hình gập: vùng phím giới hạn ~600dp canh giữa, không còn
  kéo dãn hết chiều ngang.

## 1.2.0 (versionCode 14)

**Giao diện mới**
- Phím có bóng đổ, bo góc mềm, bảng màu sáng/tối tinh chỉnh; Enter tô màu
  nhấn (accent). Icon vẽ vector thay ký tự ⇧ ⌫ ↵ (hiển thị đồng nhất mọi máy):
  shift rỗng/đặc/caps lock có vạch dưới; Enter đổi icon theo ô (🔍 tìm kiếm,
  gửi, đi tiếp, xong, xuống dòng).
- Hàng q..p hiện số gợi ý góc phím (nhấn giữ ra số); phím cách hiện
  "HKey · Tiếng Việt / English"; bong bóng phím + dải ký tự phụ có bóng, ô
  đang chọn tô màu nhấn; ký tự phụ tự viết hoa khi đang Shift (ê -> Ê).
- Thanh gợi ý: chạm có hiệu ứng, từ dài tự cắt "…".
- Mọi trang (chữ / ký hiệu / emoji) cùng chiều cao — đổi trang không làm
  app nhảy layout.

**Sửa lỗi bàn phím bị ẩn khi bấm Space / Enter**
- Vuốt phím cách dời con trỏ bằng `setSelection` thay cho phím DPAD: DPAD ở
  cuối/đầu ô làm app chuyển focus sang view khác -> bàn phím ẩn. Thêm ngưỡng
  vuốt 22dp nên chạm space hơi lệch không còn bị tính là vuốt.
- Enter ở ô nhiều dòng chèn `\n` trực tiếp (không mô phỏng KeyEvent thiếu cờ
  bàn phím mềm); ô nhiều dòng có action DONE mặc định -> xuống dòng thay vì
  đóng bàn phím. Phím ENTER thật (ô một dòng / terminal) gửi qua
  `sendDownUpKeyEvents` đủ cờ `FLAG_SOFT_KEYBOARD | KEEP_TOUCH_MODE`.

**Đủ phím**
- Thêm "/" (gạch chéo) và 2 trang ký hiệu kiểu Gboard (`?123` và `=\<`):
  / \ | ~ ` ^ [ ] { } < > = % _ € £ ¥ ₫ ° • √ π ÷ × © ✓ …; nhấn giữ ra biến
  thể (– — ± ≠ ≤ ≥ “ ” ¿ ¡ ½ ² …).
- Ô URL: phím cạnh `?123` thành "/", ô email thành "@" (vẫn giữ để ra emoji).
- Ô số / điện thoại / ngày giờ mở thẳng trang số.

**Emoji (giữ phím , )**
- Sửa lỗi trang emoji cao ~19 hàng tràn màn hình (đẩy mất hàng ABC, không
  quay lại được). Giờ là lưới cuộn dọc (kéo + vuốt quán tính) cao bằng bàn
  phím, chia 6 nhóm + tab "gần đây" (lưu lại giữa các lần), hàng dưới
  ABC | nhóm | 📋 | ⌫. Emoji có FE0F (❤️ ☺️ ✌️ …) hiện đúng dạng màu.
- Giữ , khi đang gõ dở: chốt từ trước rồi mới mở emoji.

**Sửa lỗi khác (tự rà soát)**
- ⌫ khi đang bôi chọn xoá cả vùng chọn (trước xoá ký tự trước vùng chọn);
  ⌫ sau emoji xoá trọn emoji (trước xẻ đôi surrogate để lại "�").
- Đa chạm: long-press của ngón trước không còn bắn nhầm cho ngón sau.
- Dải ký tự phụ dài hơn bề ngang không còn crash (`coerceIn` min > max).
- Rò bộ nhớ: listener TalkBack được gỡ khi view bị huỷ (mỗi lần đổi cài
  đặt trước đây giữ lại view cũ).
- Âm phím phát qua AudioManager theo cờ của HKey (Space/Enter/⌫ có âm riêng)
  — không còn im lặng khi tắt "âm chạm" hệ thống.
- Giữ VI/EN khi máy chỉ có 1 bộ gõ: mở bảng chọn bàn phím thay vì không làm gì.

## 1.1.1 (versionCode 13)

Sửa theo review code 1.1.0 — mỗi lỗi một commit, có thể revert riêng:

- **⌫ xoá đúp 2–3 lần:** phím lặp bắn thêm khi nhả ngón và repeater không
  dừng khi trượt tay khỏi ⌫. Tách logic chạm phím ra `KeyTouchState` thuần
  Kotlin — test JVM bằng `KeyTouchStateTest` (chạm-nhả = 1, giữ = lặp đúng
  nhịp, trượt/cancel = dừng).
- **Phím nổi hiện sai chỗ:** bỏ PopupWindow dùng `getLocationOnScreen`
  (lệch dưới edge-to-edge targetSdk 35); bong bóng và dải ký tự phụ giờ vẽ
  trong `onDraw` của KeyboardView, hàng trên tràn lên thanh gợi ý. Ít IPC
  WindowManager mỗi lần gõ.
- **Tắt rung vẫn rung:** cờ âm thanh/rung đẩy xuống view mỗi lần bàn phím
  hiện (kể cả khi view tái sử dụng); rung nhấn giữ theo cờ.
- **Emoji gộp vào phím `,`:** hàng dưới `?123 | , | VI | Space | . | ↵` —
  giữ `,` mở emoji (icon 😊 góc phím), đổi IME chuyển sang giữ VI/EN, space
  rộng 2.6 → 4.0.
- **Chọn emoji xong tự về trang chữ**; đổi trang dọn sạch chạm đa điểm đang
  dở (không còn phím "ma").
- **Nhanh hơn:** mỗi phím chỉ ~1 lệnh đồng bộ sang app đích (bản sao cục bộ
  40 ký tự cuối `TailTracker`, đọc lại chỉ khi con trỏ đổi từ ngoài); cache
  trạng thái TalkBack thay vì hỏi mỗi MotionEvent; chỉ vẽ lại vùng phím đổi;
  gợi ý gom một lần mỗi frame; chỉ dựng lại engine/macro khi đổi cài đặt.

## 1.1.0 (versionCode 12)

Bản lớn nhiều giai đoạn (được tag từ nhánh `fix/phase1-p0`):

- **Sửa nhanh (1.1–1.10):** "." chỉ kết câu khi có khoảng trắng theo sau
  ("hu.io.vn" không bật hoa/sửa); giữ kiểu hoa từng ký tự ("USA" không về
  "Usa"); Telex không phá tiếng Anh/URL — restore phím thô + huỷ bằng gõ
  lặp; cờ NO_SUGGESTIONS không còn tắt Telex; Enter gọi action ô (send/
  search/go); ⌫ xoá theo ký tự hiển thị; resume giữ dấu sẵn có; build fail
  rõ khi thiếu secret ký; sửa nhóm lỗi học/repeat/inset/landscape.
- **Kiểu gõ (2.x):** VNI / Simple / Quick Telex, kiểu dấu cũ-mới, spell-check,
  macro gõ tắt, phím EN/VI.
- **Bàn phím tự vẽ (3.x):** một View duy nhất — hit theo ô không rớt khe,
  trượt ngón đổi phím, đa chạm, nhấn giữ ra ký tự phụ, giữ ⌫ lặp xoá, vuốt
  space dời con trỏ, TalkBack qua node ảo, thanh candidate theo theme.
- **Model nhị phân (4.x):** `vi_model.bin` đọc mmap không parse string;
  từ điển sạch + từ ghép; học đúng kiểu hoa; không xẻ đôi surrogate emoji.
- **Build (5.x):** R8 + shrinkResources cho release.

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
