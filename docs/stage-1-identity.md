# Stage 1 — Identity và Authorization cho backend

Stage này triển khai xác thực Clerk JWT, đồng bộ user local, role USER/MANAGER/ADMIN
và phạm vi quản lý theo property. Không thay đổi frontend. Chưa có Clerk application
thật ở thời điểm triển khai; đăng nhập Google/Email và kết nối tenant thật cần kiểm
tra sau khi chủ dự án tạo Clerk application.

## Phạm vi so với implementation plan

| Hạng mục | Trạng thái |
| --- | --- |
| P1.1 — Clerk application, Google, Email/Password | Cần chủ dự án tạo/cấu hình tenant |
| P1.2 — Clerk provider, login UI, route guard FE | Để sau theo yêu cầu chỉ làm BE |
| P1.3 — Backend verify Clerk JWT | Đã triển khai, kiểm thử chữ ký và claims |
| P1.4 — Local user synchronization | Đã triển khai, upsert chống trùng và bảo vệ profile mới |
| P1.5 — Role authorization | Đã triển khai bằng role/status trong PostgreSQL |
| P1.6 — Manager → Property | Đã triển khai FK, assignment API và kiểm tra quyền ở service |

Không đánh dấu exit gate toàn Stage 1 của cả FE/BE là hoàn tất cho đến khi login
thật và tích hợp Clerk được xác nhận.

## Kiến trúc

```text
Bearer session token
  -> Spring Security Resource Server
  -> Nimbus: RS256 + Clerk JWKS + issuer/time/session/azp validation
  -> LocalUserAuthenticationConverter
  -> UserSyncService (transaction)
     -> UserIdentityWriter (atomic PostgreSQL upsert)
     -> UserRepository (JPA read)
  -> CurrentUser + ROLE_* lấy từ database
  -> Controller -> service permission check -> repository -> PostgreSQL
```

Module `identity` sở hữu user, profile và xác thực. `authorization` sở hữu gán
manager/property và kiểm tra scope. `property` mới chứa định danh chi nhánh tối
thiểu. Controller xử lý HTTP, service giữ transaction/business rules, repository
đọc/ghi dữ liệu. PostgreSQL-specific upsert được cô lập trong `UserIdentityWriter`.

## Thiết lập Clerk khi đã tạo application

1. Tạo Clerk application cho development, bật Google và Email/Password trong cấu
   hình đăng nhập của Clerk. Không lưu business role hoặc property assignment tại Clerk.
2. Điền issuer chính xác trong `.env`, ví dụ:

   ```dotenv
   CLERK_ISSUER=https://your-instance.clerk.accounts.dev
   CLERK_AUTHORIZED_PARTIES=http://localhost:3000
   CLERK_AUDIENCE=
   ```

3. Khởi động lại backend. Public keys được tải từ issuer cộng
   `/.well-known/jwks.json`, được cache và refresh khi đổi signing key. Backend
   không cần publishable key hoặc secret key để xác minh chữ ký.
4. Dùng **session token** từ phiên login thật, gửi `Authorization: Bearer <token>`.
   Swagger có nút Authorize cho scheme `clerkBearer`. Không dùng ID token Google,
   Clerk API key hoặc token template thiếu session ID.

Để trống issuer: app vẫn khởi động, health/Swagger vẫn public, nhưng mọi bearer
token bị trả 401. Đây là trạng thái mặc định khi chưa có Clerk. Không có profile
runtime nào bỏ qua xác thực. Issuer đã điền phải dùng HTTPS, không chứa credentials,
query, fragment hay dấu `/` cuối. Sai cấu hình làm startup thất bại.

Chữ ký chỉ chấp nhận RS256. Bộ kiểm tra yêu cầu issuer đúng, `exp`, `nbf`, `iat`,
subject `user_*`, session ID `sess_*` và `azp` thuộc allowlist. Có độ lệch đồng hồ
60 giây cho kiểm tra thời gian. Session `sts=pending` bị từ chối. Lumora yêu cầu
`azp` có mặt, chặt hơn lựa chọn bỏ qua claim thiếu trong hướng dẫn Clerk.
Nếu cấu hình audience thì `aud` phải chứa giá trị đó; mặc định không yêu cầu aud.

CORS và `azp` là hai kiểm tra khác nhau: CORS kiểm tra origin của request browser;
`azp` kiểm tra origin nơi token được phát hành. Request từ curl/Swagger vẫn phải
mang token có `azp` hợp lệ. Backend không tin các URL `jku`/`x5u` do token tự cung cấp.

Tham chiếu: [Clerk manual verification](https://clerk.com/docs/guides/sessions/manual-jwt-verification)
và [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).

## Đồng bộ profile

`clerk_user_id` là khóa identity duy nhất. Lần đầu tạo UUID local, role USER,
status ACTIVE. Email không phải khóa định danh và không unique: đổi email không
tạo user mới; hai Clerk identities cùng email không bị gộp.

Clerk session token mặc định không cung cấp đầy đủ profile. User vẫn đăng nhập
được với token chuẩn, email/tên có thể null. Để có profile, vào Sessions → Customize
session token và thêm ba claim đã ký:

```json
{
  "email": "{{user.primary_email_address}}",
  "first_name": "{{user.first_name}}",
  "last_name": "{{user.last_name}}"
}
```

Không đưa role/property vào cấu hình này. Backend chỉ đồng bộ ba trường profile
trên. `phone` hiện là dữ liệu local, chưa có API chỉnh sửa. Claim thiếu giữ nguyên
giá trị local; token có `iat` cũ hơn hoặc bằng lần sync gần nhất không ghi đè profile.
Role/status/local ID không bao giờ nằm trong nhánh update của upsert.

Profile đổi tại Clerk chỉ phản ánh sau khi backend nhận token mới hơn. Luồng này
không gọi Clerk Backend API, không dùng webhook, và chưa tự đồng bộ thao tác
xóa/ban user ở Clerk. Token hợp lệ còn hạn có thể tiếp tục được chấp nhận đến hết
thời gian xác minh cho phép; khi cần khóa ngay trên Lumora, đặt local status DISABLED.

Tham chiếu: [Clerk custom session claims](https://clerk.com/docs/guides/sessions/customize-session-tokens)
và [Clerk shortcode examples](https://clerk.com/docs/guides/sessions/jwt-templates).

## Database migrations

- V1 giữ nguyên checksum và nội dung.
- V2 tạo `users`, role/status constraint, `clerk_user_id UNIQUE`, profile timestamp
  và trigger `updated_at`.
- V3 tạo `properties` tối thiểu (`id`, `name`, `slug`, `status`, timestamps) và
  `user_properties` với FK thật, unique `(user_id, property_id)` và index chiều property.

Bảng property tối thiểu được đưa từ P2.1 lên để giải quyết phụ thuộc của P1.6.
Stage 2 sẽ thêm address/city/check-in/check-out và inventory bằng migration mới,
không sửa V3. Stage này chưa có API tạo/sửa property, chưa có room hay booking.
Database mới không tự seed user, admin hoặc property giả. Tests tự tạo fixture
trong database test và xóa đúng fixture của từng test.

## API contracts

Tất cả ID trong URL là UUID local, không phải `clerk_user_id`.

| Method và URL | Quyền | Kết quả |
| --- | --- | --- |
| `GET /api/me` | User có session hợp lệ và local ACTIVE | Profile local, role và status |
| `GET /api/admin/users/{userId}` | ADMIN | Profile của user được chỉ định |
| `GET /api/admin/users/{userId}/properties` | ADMIN | Danh sách property UUID đã gán |
| `PUT /api/admin/users/{userId}/properties/{propertyId}` | ADMIN | Gán property cho manager ACTIVE, 204 |
| `DELETE /api/admin/users/{userId}/properties/{propertyId}` | ADMIN | Thu hồi assignment, 204 |
| `GET /api/manager/properties` | MANAGER hoặc ADMIN | MANAGER chỉ thấy property được gán; ADMIN thấy tất cả |
| `GET /api/manager/properties/{propertyId}` | ADMIN hoặc MANAGER được gán | Property summary |

Ví dụ `/api/me`:

```json
{
  "id": "7a0ca6b2-32e8-4ca8-8096-91143b3cc228",
  "clerkUserId": "user_example",
  "email": "guest@example.com",
  "firstName": "Guest",
  "lastName": null,
  "phone": null,
  "role": "USER",
  "status": "ACTIVE",
  "createdAt": "2026-10-15T00:00:00Z",
  "updatedAt": "2026-10-15T00:00:00Z"
}
```

PUT gán trùng và DELETE assignment đã xóa đều idempotent. Gán cho USER/ADMIN hoặc
manager DISABLED trả 409. User/property không tồn tại trả 404; UUID sai format trả
400. Manager không được gán một property nhận 403 trước khi query nội dung property.
ADMIN bypass property assignment, nhưng vẫn phải là local account ACTIVE.

Role/status được đọc lại mỗi authenticated request. Thu hồi assignment, đổi role
hoặc khóa user có hiệu lực từ request kế tiếp, kể cả token cũ chưa hết hạn. Request
đang xử lý không bị hủy giữa chừng. Nếu demote manager, các assignment cũ vẫn lưu
nhưng không có hiệu lực với role USER; chủ động xóa assignment nếu không muốn khôi
phục khi promote lại sau này.

Các lỗi authentication/authorization vẫn dùng `{code, message, details, requestId}`:
401 UNAUTHORIZED; 403 FORBIDDEN; 403 ACCOUNT_DISABLED; 503 SERVICE_UNAVAILABLE nếu
kho identity tạm không truy cập được. Response 401 có `WWW-Authenticate: Bearer`.
Không log bearer token, không trả thông tin kết nối database trong lỗi.

## Cấp quyền admin đầu tiên

Không tự nâng quyền user đăng ký đầu tiên và không có endpoint bootstrap public.
Sau khi tài khoản chủ dự án đã gọi `/api/me` thành công, dùng quyền vận hành database
để promote đúng Clerk subject. Ví dụ SQL bên dưới là hướng dẫn, không được tự chạy
khi startup hoặc trong migration:

```sql
UPDATE users
SET role = 'ADMIN'
WHERE clerk_user_id = 'user_REPLACE_WITH_OWNER_ID'
  AND status = 'ACTIVE'
RETURNING id, clerk_user_id, role;
```

Xác nhận trả về đúng một user. Cấp MANAGER tương tự với `role = 'MANAGER'`, rồi dùng
assignment API. Chức năng quản lý role/status qua API sẽ thuộc phase admin operations.
Không dùng email để bootstrap vì identity phải bám Clerk subject.

## Kiểm chứng và bước tích hợp còn lại

```powershell
.\mvnw.cmd verify
# Chạy với DB test riêng và environment DB_* tương ứng:
.\mvnw.cmd -Pintegration verify
```

Unit tests dùng RSA key tạo tạm để kiểm chữ ký, JWKS/key rotation, thuật toán,
issuer, expiry/not-before, authorized party, audience và session claims.
Integration tests gửi bearer token đã ký qua HTTP thật vào Spring Security, chạy
JPA/Flyway/PostgreSQL thật; không dùng mocked authentication để bỏ qua JWT.
Test decoder chỉ có trong `src/test`, không được đóng gói vào application JAR.

Kết quả kiểm chứng ngày 2026-10-15: build thành công, 34 unit tests và 12 integration
tests đều pass (46 tests, không có failure/error). Integration tests chạy trên
PostgreSQL 17 với database test riêng; đã kiểm tra migration V1 → V2 → V3 trên
database mới và chạy lại khi schema đã cập nhật. Kết quả này chưa bao gồm xác minh
đăng nhập với Clerk tenant thật.

Khi có Clerk tenant: bật hai phương thức đăng nhập, điền issuer/origin, kiểm tra
Google và Email/Password thực tế, dùng session token gọi `/api/me`, promote owner
đúng subject rồi kiểm tra admin API. Frontend authentication và end-to-end browser
login sẽ làm ở lượt FE được yêu cầu riêng.
