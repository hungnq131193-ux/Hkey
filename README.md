# HKey - Bàn phím Android Tiếng Việt Thông Minh

[![Android CI & Release](https://github.com/hungnq131193-ux/HKey/actions/workflows/android.yml/badge.svg)](https://github.com/hungnq131193-ux/HKey/actions/workflows/android.yml)

## Giới thiệu
HKey là bàn phím tiếng Việt mã nguồn mở nhẹ, bảo mật và thông minh dành cho Android:
- **Bộ gõ Telex:** Tự động nhận diện thanh dấu chuẩn tiếng Việt (`s`, `f`, `r`, `x`, `j`, `w`, `aa`, `ee`, `oo`, `dd`).
- **Từ điển tiếng Việt tích hợp:** Hơn 7.400 tiếng và từ lóng/loanword phổ biến nạp sẵn trong app — nhận diện đúng từ hợp lệ, gợi ý hoàn thành và sửa lỗi chính xác hơn, không cần mạng.
- **Gợi ý từ theo ngữ cảnh (Context Prediction):** Mô hình Unigram/Bigram/Trigram thống kê từ Wikipedia tiếng Việt (~6 nghìn âm tiết — đã lọc từ ngoại/ngữ/mã web — ~240 nghìn cụm) để dự đoán từ tiếp theo và xếp hạng gợi ý theo mạch câu văn.
- **Tự sửa lỗi (Context Auto-correction):** Phát hiện và sửa từ gõ sai bảo thủ — chỉ đụng từ có dạng âm tiết tiếng Việt (không sửa tiếng Anh/mã), xét ngữ cảnh, tần suất và phím kề trên QWERTY; không chắc thì không sửa. Sửa nhầm? Bấm `⌫` hoặc chạm từ gốc trên thanh gợi ý để khôi phục nguyên văn đã gõ — app tự học và không sửa oan lần sau.
- **Càng gõ càng thông minh (Dynamic Learning):** Tự động ghi nhớ từ và cụm từ cá nhân bạn hay dùng để tối ưu danh sách gợi ý. Dữ liệu học được lưu bền trên máy và có giới hạn dung lượng.

## Quyền riêng tư
- **Không gửi dữ liệu đi đâu:** Gõ phím hoạt động hoàn toàn offline; dữ liệu học chỉ lưu trong bộ nhớ riêng của app trên máy bạn. Chỉ tính năng **Cập nhật** trong app cài đặt mới dùng mạng — tải APK bản mới từ GitHub Releases — không gửi nội dung gõ hoặc dữ liệu học.
- **Tự tắt trong ô nhạy cảm:** Telex, gợi ý, tự sửa và tự học đều tắt hoàn toàn trong ô mật khẩu, email, URL và các ô không cho phép gợi ý — ký tự gõ được giữ nguyên.
- **Không sao lưu lên cloud:** `allowBackup=false` nên dữ liệu học không bị backup tự động.
- **Xoá dữ liệu học:** Mở app HKey → mục "Quyền riêng tư" → nút **Xóa dữ liệu học**.

## Dữ liệu mô hình
Mô hình ngôn ngữ (`res/raw/vi_model.bin`) được thống kê từ nội dung Wikipedia tiếng Việt, phát hành theo giấy phép [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/deed.vi). Chỉ chứa tần suất từ/cụm từ, không chứa nội dung bài viết. Công cụ sinh: `tools/build_model.py`.

## Cài đặt & Build
- Tải file APK mới nhất tại tab [Releases](https://github.com/hungnq131193-ux/HKey/releases).
- Hoặc tự build từ source:
  ```bash
  ./gradlew assembleRelease
  ```
