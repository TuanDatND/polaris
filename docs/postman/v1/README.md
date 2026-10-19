# Polaris V3 - Postman

## Chuẩn bị

- App Polaris chạy ở `http://localhost:8080`; PostgreSQL và Docker hoạt động.
- Có sẵn một user đăng nhập được qua Spring form login. Hiện source chưa expose registration controller, nên collection không tự tạo user hoặc giả định tài khoản mặc định.
- Image `nginx:alpine` tồn tại trong đúng Docker daemon mà Polaris sử dụng. Provider hiện gọi createContainer trực tiếp, chưa pull image tự động. Bạn có thể tự chuẩn bị bằng `docker pull nginx:alpine`.
- Chọn image chạy lâu dài. `hello-world` chạy xong sẽ thoát, nên không phù hợp cho bài kiểm tra RUNNING.

## Import và chạy

1. Import `Polaris-V3.postman_collection.json` và `Polaris-Local.postman_environment.json`.
2. Chọn environment **Polaris Local**, điền `username` và `password` của tài khoản có sẵn. Giữ credential ở giá trị local của Postman.
3. Mở Collection Runner, chọn toàn bộ 16 request, **Iterations = 1**, **Delay = 1000 ms**. Giữ cookie jar bật; redirect được tắt ở từng request để tránh nhầm login HTML là API thành công.
4. Chạy collection từ request 01. Mỗi lần chạy tạo tenant/name riêng. Collection tự lưu `tenant_id`, `instance_id`, CSRF token.
5. Các request Wait lặp GET bằng `pm.execution.setNextRequest(pm.info.requestId)`. Chúng chỉ tự lặp trong Runner/CLI, không tự lặp khi bấm Send từng request. Tối đa mặc định 120 lần cho mỗi giai đoạn; với Delay = 1000 ms là khoảng hai phút cộng thời gian HTTP.

## Vì sao có các request authentication?

SecurityConfig hiện dùng form login và CSRF cho API. Request 01 lấy hidden `_csrf` từ GET /login; request 02 gửi form đăng nhập. Postman giữ session cookie. Sau login, Spring thay token nên request 03 lấy lại token. Các POST/DELETE tiếp theo gửi `X-CSRF-TOKEN`. Bearer token riêng lẻ chưa được application security chain hiện tại cấu hình để xác thực.

## Các kiểm tra

- Đăng nhập + CSRF theo cấu hình thật.
- Tạo tenant độc lập với quota một instance.
- CPU = 0 trả 400 / VALIDATION_FAILED.
- Create trả 202, rồi GET xác nhận RUNNING và containerId.
- Trùng tên trả 409 / DUPLICATE_RESOURCE.
- Vượt số lượng instance trả 409 / QUOTA_EXCEEDED.
- Stop và start: kiểm tra desired ngay sau 202, rồi chờ current hội tụ.
- Delete từ RUNNING: chờ DELETED và containerId null.
- Gọi DELETE lần nữa vẫn 202 và DELETED.

202 chỉ chứng minh command được nhận. Các request Wait mới chứng minh currentState đã hội tụ. Script dừng collection khi assertion thất bại hoặc instance FAILED để giữ nguyên bằng chứng chẩn đoán.

## Dữ liệu được tạo và giới hạn

Mỗi run tạo một tenant còn được giữ lại (chưa có tenant DELETE API). Run thành công xóa provider instance thông qua API; instance DB record vẫn DELETED theo mô hình hiện tại. Nếu thất bại sau create, collection không tự cleanup để bạn xem log; dùng request 14 với instance_id đã lưu khi muốn cleanup. Chạy request 15 để theo dõi kết quả.

Collection chỉ kiểm tra API response. InstanceResponse chưa expose quotaReleased, generation, observedGeneration hoặc reconcile request status, nên không khẳng định quota đã trả hay request đã COMPLETED. Chưa kiểm tra multi-worker race, delete-during-provisioning hoặc provider drift.

## Khi thất bại

- Request 01: thiếu credential hoặc app chưa chạy; điền environment và kiểm tra URL.
- Request 02: redirect /login?error là sai credential; không coi HTTP 302 tự nó là login thành công.
- API trả 302/401/403: kiểm tra cookie jar, session và CSRF; chạy lại từ request 01.
- Wait thấy FAILED: đọc failureReason và log CREATE task. Image chưa có trong Docker daemon là một khả năng.
- Wait hết giới hạn: kiểm tra TaskWorker/ReconcileWorker, log provider và database queue. Tăng max_poll_attempts nếu provider thực sự cần thêm thời gian.

File đã được kiểm tra cấu trúc JSON và cú pháp JavaScript; chưa chạy API thật vì cần tài khoản/session và dịch vụ local của bạn.
