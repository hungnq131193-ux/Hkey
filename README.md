# HKey - Bàn phím Android Tiếng Việt Thông Minh

[![Android CI & Release](https://github.com/hungnq131193-ux/HKey/actions/workflows/android.yml/badge.svg)](https://github.com/hungnq131193-ux/HKey/actions/workflows/android.yml)

## Giới thiệu
HKey là bàn phím tiếng Việt mã nguồn mở nhẹ, bảo mật và thông minh dành cho Android:
- **Bộ gõ Telex:** Tự động nhận diện thanh dấu chuẩn tiếng Việt (`s`, `f`, `r`, `x`, `j`, `w`, `aa`, `ee`, `oo`, `dd`).
- **Gợi ý từ theo ngữ cảnh (Context Prediction):** Áp dụng mô hình Bigram/Trigram để dự đoán chính xác từ tiếp theo theo mạch câu văn.
- **Tự sửa lỗi (Context Auto-correction):** Tự động phát hiện và sửa từ gõ sai dựa trên từ liền trước và khoảng cách phím (Levenshtein Distance).
- **Càng gõ càng thông minh (Dynamic Learning):** Tự động ghi nhớ tần suất sử dụng cụm từ cá nhân để tối ưu danh sách gợi ý.

## Cài đặt & Build
- Tải file APK mới nhất tại tab [Releases](https://github.com/hungnq131193-ux/HKey/releases).
- Hoặc tự build từ source:
  ```bash
  ./gradlew assembleRelease
  ```
