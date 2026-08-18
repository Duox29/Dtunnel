# Hướng dẫn vận hành — Quản trị viên (SUPERADMIN)

Tài liệu này dành cho **SUPERADMIN**: người triển khai hệ thống, quản lý node
gateway, xét duyệt yêu cầu của người dùng và giám sát vận hành.

> Phần sử dụng thông thường (đăng ký, xin port, tạo tunnel) nằm ở
> [user-guide.md](user-guide.md).

---

## 1. Vai trò SUPERADMIN

Hệ thống hiện có 2 vai trò: `USER` và `SUPERADMIN`. SUPERADMIN có toàn quyền:

- Đăng ký/quản lý **node gateway**, seed dải port, xoay node token.
- **Duyệt/từ chối** resource request (cấp port).
- **Duyệt/thu hồi** agent (thiết bị người dùng).
- Xem **toàn bộ** request, tunnel, agent, port của mọi user.
- Đọc **audit trail** (nhật ký thao tác kèm IP nguồn).

Mọi hành động SUPERADMIN đều được ghi audit — kể cả thao tác của chính bạn.

---

## 2. Tài khoản SUPERADMIN đầu tiên

Khi Control Plane khởi động lần đầu, nó tự seed một SUPERADMIN:

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `DTUNNEL_SUPERADMIN_EMAIL` | `admin@duox.local` | Email đăng nhập |
| `DTUNNEL_SUPERADMIN_PASSWORD` | `admin-change-me` | Mật khẩu |

> ⚠️ **Đổi mật khẩu bootstrap ngay khi triển khai thật.** Đặt hai biến trên
> thành giá trị mạnh trước khi chạy compose. Tài khoản chỉ được seed nếu email
> chưa tồn tại.

Đăng nhập tại dashboard (`http://localhost:3000`) bằng tài khoản này — menu
**Admin** sẽ xuất hiện ở sidebar.

---

## 3. Triển khai hệ thống

### 3.1. Stack đầy đủ (khuyến nghị)

```bash
cd deploy/compose
docker compose up -d --build
```

Các service:

| Service | Cổng ra host | Vai trò |
|---|---|---|
| `postgres` | — (nội bộ) | Nguồn sự thật: user, node, port, tunnel, audit |
| `redis` | — (nội bộ) | Session, rate limit, agent token, ShedLock |
| `control-plane` | **8080** (REST), **9091** (gRPC agent) | API + job nền + plugin endpoint |
| `web` | **3000** | Dashboard (nginx SPA, proxy `/api` + `/agent` sang control-plane) |
| `frps-vn01` | 7000 (bind), 7500 (admin API), 20000–20100 (tunnel) | Gateway FRP mẫu |
| `caddy` | **8090** | HTTP edge cho tunnel domain (dev); TLS tự động trong production |

Kiểm tra sau khi chạy:

- Dashboard: http://localhost:3000
- Swagger: http://localhost:8080/swagger-ui.html
- Health: `curl http://localhost:8080/actuator/health`

### 3.2. Biến môi trường quan trọng của control-plane

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | jdbc:postgresql://localhost:5432/tunnelplatform | Kết nối Postgres |
| `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT` | localhost:6379 | Redis |
| `DTUNNEL_SUPERADMIN_EMAIL` / `_PASSWORD` | admin@duox.local / admin-change-me | Seed SUPERADMIN |
| `DTUNNEL_FRP_PLUGIN_TOKEN` | _(rỗng)_ | Shared secret cho frps plugin. **Đặt giá trị mạnh trong production**; để rỗng = không kiểm tra token plugin |
| `COOKIE_SECURE` | `false` | Đặt `true` khi chạy sau HTTPS |
| `dtunnel.agent.token-ttl` | `PT24H` | Thời hạn token thiết bị |
| `dtunnel.agent.stale-threshold` | `PT60S` | Agent không heartbeat quá ngưỡng → OFFLINE |
| `dtunnel.node.stale-threshold` | `PT120S` | Node không heartbeat quá ngưỡng → OFFLINE |
| `dtunnel.ratelimit.enabled` | `true` | Bật/tắt rate limit |

Schema do **Flyway** quản lý (V1→V5), Hibernate chỉ `validate` — không sửa
schema thủ công.

### 3.3. Cấu hình frps trên mỗi node

File mẫu: `deploy/frps/frps-vn01.toml`. Nguyên tắc: **frps cấu hình một lần,
không sửa khi thêm tunnel** — việc ủy quyền từng kết nối do plugin đảm nhận.

```toml
bindPort = 7000
vhostHTTPPort = 8081          # chỉ cần nếu node phục vụ tunnel HTTP

webServer.addr = "0.0.0.0"    # admin API — control plane đọc để đo lưu lượng
webServer.port = 7500

allowPorts = [ { start = 20000, end = 20100 } ]   # dải port node sở hữu

[[httpPlugins]]
  name = "duox-authz"
  addr = "http://control-plane:8080"
  path = "/agent/v1/frp-plugin?token="            # token nối vào query (frps không gửi được custom header)
  ops = ["Login", "NewProxy", "Ping", "CloseProxy"]
```

Lưu ý vận hành:

- Dải `allowPorts` trong frps.toml **phải khớp** dải bạn seed vào database
  (mục 4.2).
- `?token=` trong path phải khớp `DTUNNEL_FRP_PLUGIN_TOKEN`.
- Với node chạy ngoài compose, dùng `deploy/frps/frps-local.toml` làm mẫu
  (plugin trỏ về `host.docker.internal:8080`).

### 3.4. Stack kiểm thử E2E (control plane chạy trên host)

```bash
cd deploy/compose
docker compose -f docker-compose.e2e.yml up -d   # chỉ postgres + redis + frps
# chạy control plane trên host (JDK 25):
cd ../../dtunnel && JAVA_HOME=~/.jdks/jdk-25.0.4+7 ./mvnw spring-boot:run
```

### 3.5. Observability (Prometheus + Grafana + Loki)

```bash
cd deploy/observability
docker compose -f docker-compose.observability.yml up -d
```

- Prometheus: http://localhost:9090 (scrape `/actuator/prometheus`)
- Grafana: http://localhost:3001 (admin/admin, datasource tự provisioning)
- Loki: http://localhost:3100

Stack này join network của compose chính (`compose_default`) — chạy stack
chính trước.

---

## 4. Quản lý node gateway

### 4.1. Đăng ký node

Trên dashboard: **Admin → Nodes (SUPERADMIN)** → điền form **Register gateway node**:

| Trường | Ví dụ | Ý nghĩa |
|---|---|---|
| Code | `VN-01` | Mã node, duy nhất, hiện khắp UI |
| Region | `vietnam` | Khu vực |
| Public address | `203.0.113.10` | IP/domain người dùng kết nối tới (endpoint tunnel, ping test) |
| frps admin URL | `http://frps-vn01:7500` | Nơi control plane đọc bộ đếm lưu lượng (đặt được cả sau khi đăng ký) |

Node đăng ký xong có ngay **nodeToken** (shared secret cho node agent) — xem
trong JSON trả về hoặc cột chi tiết node. **Lưu lại token này**: cần để chạy
`duox-node-agent`.

Qua API:

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/v1/nodes \
  -H 'Content-Type: application/json' \
  -d '{
    "code": "VN-01",
    "region": "vietnam",
    "publicAddress": "203.0.113.10",
    "protocolCapabilities": ["TCP", "UDP"],
    "frpsAdminUrl": "http://frps-vn01:7500",
    "vhostHttpPort": 8081
  }'
```

- `protocolCapabilities` mặc định `["TCP","UDP"]` nếu bỏ trống.
- `vhostHttpPort`: đặt khi node phục vụ tunnel HTTP (phải khớp
  `vhostHTTPPort` trong frps.toml). Có thể cập nhật sau bằng
  `PATCH /api/v1/nodes/{id}`.

### 4.2. Seed dải port

Mỗi node cần danh sách port trong database để hệ thống cấp phát. Trên UI, bấm
**Seed ports** (mặc định seed TCP 20000–20100 — khớp frps.toml mẫu).

Qua API (tùy chọn dải khác):

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/v1/nodes/<node-id>/ports/seed \
  -H 'Content-Type: application/json' \
  -d '{"protocol":"TCP","start":20000,"end":20100}'
# {"created":101}
```

Quy tắc:

- Idempotent — port đã tồn tại bị bỏ qua, chạy lại an toàn.
- Range tối đa 50.000 port/lần; phải nằm trong 1–65535.
- Port cấp phát dùng `SELECT ... FOR UPDATE SKIP LOCKED` + partial unique
  index `uq_port_live` → không bao giờ cấp trùng port đang dùng.

### 4.3. Chạy Node Agent (duox-node-agent)

Node agent chạy **trên máy gateway** (cùng máy frps), báo cáo sức khỏe/tài
nguyên (load, RAM, disk, số proxy frps) về control plane. Nó dùng **node
token**, không mang thông tin đăng nhập user — mô hình quyền tách biệt với
agent phía user.

```bash
cd dtunnel-agent && go build -o bin/duox-node-agent ./cmd/duox-node-agent

./bin/duox-node-agent \
  --server http://control-plane:8080 \
  --token <nodeToken> \
  --frps-admin http://127.0.0.1:7500 \
  --heartbeat 30s
```

| Cờ | Biến môi trường | Ý nghĩa |
|---|---|---|
| `--server` | `DUOX_SERVER` | URL control plane |
| `--token` | `DUOX_NODE_TOKEN` | Node token (bắt buộc) |
| `--frps-admin` | `DUOX_FRPS_ADMIN` | frps admin API để đếm proxy (tùy chọn) |
| `--heartbeat` | — | Chu kỳ heartbeat, mặc định 30s |

Node có heartbeat sẽ được giữ `ONLINE`; mất heartbeat quá 120 giây → tự động
`OFFLINE` (job `detectStaleNodes`). Node chưa từng chạy node agent (chưa có
lastSeen) không bị đánh dấu OFFLINE.

### 4.4. Xoay node token

Khi nghi ngờ lộ token hoặc thay đổi nhân sự vận hành node:

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/v1/nodes/<node-id>/rotate-token
# {"nodeId":"...","nodeToken":"<token-mới>"}
```

Token cũ **hết hiệu lực ngay** (heartbeat bị từ chối 401). Cập nhật token mới
cho `duox-node-agent` và khởi động lại nó.

### 4.5. Cập nhật endpoint node

```bash
curl -b cookies.txt -X PATCH http://localhost:8080/api/v1/nodes/<node-id> \
  -H 'Content-Type: application/json' \
  -d '{"frpsAdminUrl":"http://frps-vn01:7500","publicAddress":"203.0.113.10","vhostHttpPort":8081}'
```

Trên UI: mỗi dòng node có ô nhập **frps admin URL** + nút **Set**.

---

## 5. Duyệt yêu cầu của người dùng

### 5.1. Duyệt resource request (cấp port)

Nơi xem: **Overview/Requests → card Resource requests** (admin thấy request của
**mọi** user, kèm nút Approve/Reject cho request `PENDING`).

- **Approve**: request `PENDING → APPROVED → ALLOCATED`; port được cấp ngay
  trong cùng transaction; allocation có `expiresAt` = hiện tại + số ngày yêu
  cầu. Nếu user đề xuất port còn trống trong dải → cấp đúng port đó, nếu không
  hệ thống tự chọn.
- **Reject**: kèm lý do (tùy chọn), ghi vào audit metadata.

Qua API:

```bash
curl -b cookies.txt http://localhost:8080/api/v1/resource-requests   # tất cả request
curl -b cookies.txt -X POST http://localhost:8080/api/v1/resource-requests/<id>/approve
curl -b cookies.txt -X POST http://localhost:8080/api/v1/resource-requests/<id>/reject \
  -H 'Content-Type: application/json' -d '{"reason":"port trùng dịch vụ nội bộ"}'
```

Checklist trước khi duyệt: node còn đủ port trống? mục đích hợp lệ? số ngày
hợp lý? (Hệ thống không giới hạn số request/user ngoài rate limit 30/giờ —
chính sách hạn mức là quyết định nghiệp vụ của bạn.)

### 5.2. Duyệt agent (thiết bị)

Nơi xem: **Agents** hoặc **Admin → Agents**. Agent mới đăng ký ở trạng thái
`PENDING`.

- **Approve**: agent → `ONLINE`, bắt đầu nhận desired-state. Chỉ duyệt khi
  bạn nhận ra thiết bị (platform, thời điểm đăng ký khớp với user đã báo).
- **Revoke**: agent → `REVOKED` **ngay lập tức**:
  - Token thiết bị bị từ chối ở mọi request (filter kiểm tra mỗi lần gọi).
  - Nếu agent đang chạy kênh gRPC: nhận push `Revoked` **dưới 1 giây**, dừng
    toàn bộ tunnel.
  - frps plugin từ chối Login/NewProxy tiếp theo của agent đó.
  - Không hoàn tác được — thiết bị muốn dùng lại phải đăng ký agent mới.

Qua API:

```bash
curl -b cookies.txt http://localhost:8080/api/v1/agents                    # tất cả agent
curl -b cookies.txt -X POST http://localhost:8080/api/v1/agents/<id>/approve
curl -b cookies.txt -X POST http://localhost:8080/api/v1/agents/<id>/revoke
```

---

## 6. Giám sát hệ thống

### 6.1. Dashboard Admin

Trang **Admin** gồm 3 card:

- **Nodes**: trạng thái node, frps admin URL, capacity JSON từ node agent.
- **Agents**: mọi thiết bị, duyệt/thu hồi.
- **Audit trail**: 50 bản ghi mới nhất mỗi trang.

KPI strip ở Overview cho cái nhìn nhanh: số tunnel active, tunnel lỗi, agent
online, node online.

### 6.2. Audit trail

Mọi sự kiện quan trọng được ghi vào bảng `audits` với **IP nguồn** (hỗ trợ
`X-Forwarded-For`): đăng ký/đăng nhập, submit request, approve/reject, cấp
port, tạo/start/stop/xóa tunnel, approve/revoke agent, frp Login/NewProxy/
CloseProxy, node register/seed ports/rotate token, các chuyển trạng thái tự
động (expired, released, offline, reconcile…).

```bash
curl -b cookies.txt "http://localhost:8080/api/v1/audits?page=0&size=50"
```

Mỗi entry gồm: `actor` (userId/agentId/system), `actorType` (USER/ADMIN/
AGENT/SYSTEM), `action`, `resourceType/resourceId`, `result`, `sourceIp`,
`metadata`, `createdAt`.

### 6.3. Metrics

- `GET /actuator/prometheus` — scrape bởi Prometheus (đã cấu hình sẵn trong
  stack observability).
- `GET /actuator/health` — health check.

---

## 7. Job nền và vòng đời tự động

Tất cả job dùng `@Scheduled` + **ShedLock** (khóa trong Redis) → chạy nhiều
instance control-plane vẫn an toàn, không double-fire.

| Job | Chu kỳ | Hành vi |
|---|---|---|
| `processExpirationWarnings` | 5 phút | Allocation hết hạn trong ≤ 5 ngày → tunnel `EXPIRING` (cảnh báo UI) |
| `processExpirations` | 5 phút | Allocation quá hạn → tunnel STOPPING (agent dừng frpc), port `EXPIRED_PENDING_RELEASE`, bắt đầu **3 ngày ân hạn** |
| `processGraceReleases` | 5 phút | Hết ân hạn → port `RELEASED` (cấp lại được), tunnel `EXPIRED` |
| `detectStaleAgents` | 30 giây | Agent không heartbeat/Ping quá 60s → `OFFLINE` |
| `detectStaleNodes` | 60 giây | Node (đã có node agent) không heartbeat quá 120s → `OFFLINE` |
| `reconcileDesiredVsObserved` | 60 giây | Tunnel `STARTING` mà agent offline → `ERROR`; tunnel `ERROR` mà agent online lại → tự `STARTING` lại (tự phục hồi) |
| `aggregateUsage` | định kỳ | Tổng hợp lưu lượng vào `usage_daily` (idempotent upsert) |
| `UsageCollectorJob` | định kỳ | Đọc bộ đếm proxy từ frps admin API của các node |

> **ERROR chỉ sinh ra từ sự cố** (frpc đóng bất thường / agent mất kết nối),
> không bao giờ từ thao tác Stop của user — nên cơ chế tự khôi phục ERROR →
> STARTING là an toàn.

---

## 8. Rate limit (chống lạm dụng)

Bucket4j + Redis, chung ngân sách giữa mọi instance. Chỉ áp cho POST:

| Endpoint | Giới hạn | Theo |
|---|---|---|
| `POST /api/v1/auth/register`, `/login` | 10/phút | IP |
| `POST /agent/v1/register` | 5/phút | IP |
| `POST /api/v1/resource-requests` | 30/giờ | user |
| `POST /api/v1/nodes/{id}/ping` | 60/phút | user |

Vượt → `429` + `Retry-After`. Tắt khẩn cấp: `dtunnel.ratelimit.enabled=false`
(không khuyến nghị).

---

## 9. Bảo mật — checklist production

Đã triển khai sẵn (detail.md §15):

- ✅ Cookie phiên httpOnly + SameSite=Lax; session rotate khi đăng nhập
  (chống session fixation); thời hạn 30 phút trong Redis.
- ✅ Token thiết bị ngắn hạn (24h), không bao giờ gửi khóa riêng tư; lộ token
  không chiếm được port (NewProxy kiểm tra lại quyền sở hữu + allocation mỗi lần).
- ✅ frps plugin tái ủy quyền **mọi** Login/NewProxy: ownership, allocation còn
  hạn, node/protocol/port khớp; tunnel HTTP phải khớp domain đã đăng ký.
- ✅ Partial unique index chống cấp trùng port.
- ✅ Rate limit phân tán (Redis).
- ✅ Audit mọi hành động SUPERADMIN + revocation, kèm IP nguồn.
- ✅ Secret chỉ qua biến môi trường.

Việc **bạn** cần làm khi lên production:

1. Đổi `DTUNNEL_SUPERADMIN_PASSWORD` và mật khẩu Postgres (`change_me` trong
   compose).
2. Đặt `DTUNNEL_FRP_PLUGIN_TOKEN` giá trị mạnh và khớp `?token=` trong mọi
   frps.toml.
3. `COOKIE_SECURE=true` sau reverse proxy HTTPS.
4. Không expose frps admin API (7500) ra Internet — chỉ control plane cần đọc.
5. Giới hạn mạng cho `/actuator/prometheus` (chỉ Prometheus scrape).
6. Backup Postgres (volume `pgdata`) định kỳ.
7. Cân nhắc SMTP + xác minh email và cooldown tái tạo tài khoản (chưa có trong
   MVP — AUDIT.md §6 đã đánh dấu).

---

## 10. Xử lý sự cố vận hành

| Triệu chứng | Chẩn đoán | Xử lý |
|---|---|---|
| User báo tunnel không ACTIVE | Xem audit `frp.new_proxy` có SUCCESS không; xem log control-plane dòng `frp-plugin deny: <lý do>` | Lý do deny chỉ đúng nguyên nhân: allocation hết hạn, sai port, agent revoked… |
| frps log plugin timeout | frps không gọi được `control-plane:8080` | Kiểm tra network compose / địa chỉ plugin trong frps.toml |
| `deny: bad plugin token` | `DTUNNEL_FRP_PLUGIN_TOKEN` lệch với `?token=` | Đồng bộ hai giá trị, restart frps |
| Node agent 401 | Node token đã bị rotate | Lấy token mới từ `rotate-token`, cập nhật node agent |
| Usage không hiện số liệu | Node chưa đặt `frpsAdminUrl` hoặc admin API không đạt | `PATCH` lại URL; kiểm tra firewall tới 7500 |
| Job không chạy khi chạy 2 instance | Đó là hành vi đúng — ShedLock chỉ cho 1 instance thắng | Kiểm tra Redis nếu nghi lock kẹt (khóa có TTL) |
| Muốn dừng khẩn cấp một tunnel | Stop tunnel (user) hoặc revoke agent (mạnh hơn) | Revoke agent dừng **mọi** tunnel của thiết bị đó trong <1s qua gRPC |
| Control plane không khởi động | Postgres/Redis chưa healthy | Xem `docker compose ps`, healthcheck; Flyway tự migrate khi DB sẵn sàng |

### Thao tác khẩn cấp theo mức độ

1. **Dừng 1 tunnel**: `POST /api/v1/tunnels/{id}/stop` (hoặc bảo user tự Stop).
2. **Cắt 1 thiết bị**: `POST /api/v1/agents/{id}/revoke` — dưới 1 giây với gRPC.
3. **Cắt 1 node**: dừng frps trên node đó; node agent ngừng heartbeat → node
   OFFLINE sau 120s; mọi NewProxy mới bị plugin từ chối nếu allocation/node
   không còn hợp lệ.

---

## 11. Tóm tắt API cho SUPERADMIN

| Method + Path | Mô tả |
|---|---|
| `POST /api/v1/nodes` | Đăng ký node (trả nodeToken) |
| `PATCH /api/v1/nodes/{id}` | Cập nhật publicAddress / frpsAdminUrl / vhostHttpPort |
| `POST /api/v1/nodes/{id}/rotate-token` | Xoay node token |
| `POST /api/v1/nodes/{id}/ports/seed` | Seed dải port |
| `GET /api/v1/resource-requests` | Mọi request |
| `POST /api/v1/resource-requests/{id}/approve` | Duyệt + cấp port |
| `POST /api/v1/resource-requests/{id}/reject` | Từ chối (kèm lý do) |
| `GET /api/v1/agents` | Mọi agent |
| `POST /api/v1/agents/{id}/approve` | Duyệt thiết bị |
| `POST /api/v1/agents/{id}/revoke` | Thu hồi thiết bị |
| `GET /api/v1/ports` | Mọi port + trạng thái |
| `GET /api/v1/tunnels` | Mọi tunnel |
| `GET /api/v1/audits?page=&size=` | Audit trail |

Kênh nội bộ (không gọi từ UI): `POST /node/v1/heartbeat` (node agent),
`POST /agent/v1/frp-plugin` (frps callback), `/agent/v1/*` (user agent).

Tài liệu API tương tác: `http://<control-plane>:8080/swagger-ui.html`.
