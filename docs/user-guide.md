# Hướng dẫn sử dụng — Người dùng (USER)

Tài liệu này dành cho **người dùng cuối** của dtunnel: người muốn đưa một dịch
vụ cục bộ (SSH, web, database…) ra Internet thông qua một tunnel được quản lý,
cấp phép và đo lưu lượng.

> Phần dành cho quản trị viên (triển khai hệ thống, duyệt yêu cầu, quản lý
> node) nằm ở [admin-guide.md](admin-guide.md).

---

## 1. dtunnel là gì, hoạt động ra sao?

dtunnel là nền tảng quản lý tunnel xây trên FRP. Điểm khác biệt cốt lõi:
**máy chủ quyết định, agent chỉ thực thi**. Mọi kết nối của frpc tới gateway
đều được Control Plane xét duyệt thời gian thực — bạn không thể tự ý chiếm
port hay domain chưa được cấp.

Một vòng sử dụng hoàn chỉnh:

```text
Đăng ký tài khoản
   └► Gửi yêu cầu cấp port (resource request)
        └► Admin duyệt → bạn có port công khai (allocation, có hạn dùng)
             └► Cài duox-agent trên máy chứa dịch vụ → admin duyệt thiết bị
                  └► Tạo tunnel (trỏ về dịch vụ cục bộ) → Start
                       └► Tunnel ACTIVE → truy cập qua endpoint công khai
```

Có **2 loại tunnel**:

| Loại | Cần cấp port? | Endpoint | Dùng khi nào |
|---|---|---|---|
| **PORT** (TCP/UDP) | Có — cần allocation | `<địa-chỉ-node>:<port>` | SSH, RDP, database, game, mọi dịch vụ TCP/UDP |
| **HTTP** (domain) | Không | `http://<domain-của-bạn>` | Web service; nhiều tunnel dùng chung 1 port vhost của node, phân biệt bằng Host header |

### Các khái niệm cần nhớ

| Khái niệm | Ý nghĩa |
|---|---|
| **Node** | Máy gateway chạy frps ở một khu vực (ví dụ `VN-01`). Bạn chọn node khi xin port. |
| **Resource request** | Yêu cầu xin port công khai trên một node, trong N ngày. Chờ admin duyệt. |
| **Allocation** | Port đã được cấp cho bạn, có **hạn dùng** (expiresAt). |
| **Agent** | Bản cài `duox-agent` trên một máy của bạn. Mỗi agent có một danh tính khóa Ed25519 duy nhất. |
| **Tunnel** | Một đường dẫn cụ thể: từ port/domain công khai trỏ về `targetHost:targetPort` trên máy chạy agent. |

### Trạng thái (status) bạn sẽ gặp

**Tunnel:**

| Status | Ý nghĩa |
|---|---|
| `CONFIGURED` | Đã tạo, chưa chạy. Bấm **Start** để bắt đầu. |
| `STARTING` | Agent đang khởi động frpc cho tunnel này. |
| `ACTIVE` | Đang phục vụ lưu lượng. |
| `STOPPING` / `STOPPED` | Đang dừng / đã dừng. Start lại bất cứ lúc nào. |
| `ERROR` | frpc đóng kết nối bất thường hoặc agent mất kết nối. Hệ thống tự thử khôi phục khi agent online lại; bạn cũng có thể bấm Start. |
| `EXPIRING` | Allocation còn ≤ 5 ngày — cảnh báo gia hạn. |
| `EXPIRED` | Allocation hết hạn (sau 3 ngày ân hạn), port đã trả về hệ thống. |

**Agent:**

| Status | Ý nghĩa |
|---|---|
| `PENDING` | Mới đăng ký, chờ admin duyệt. |
| `ONLINE` | Đang hoạt động (heartbeat trong 60 giây gần nhất). |
| `OFFLINE` | Mất kết nối > 60 giây. Tunnel sẽ bị đánh dấu ERROR và tự khôi phục khi agent online lại. |
| `REVOKED` | Bị admin thu hồi. Agent mất quyền ngay lập tức, muốn dùng lại phải đăng ký thiết bị mới. |

---

## 2. Đăng ký tài khoản và đăng nhập

### Qua giao diện web

1. Mở dashboard (mặc định `http://localhost:3000` khi chạy compose).
2. Ở màn hình **Sign in**, chọn **Register**.
3. Nhập email và mật khẩu (tối thiểu 8 ký tự) → **Create account**.
4. Đăng ký thành công, bạn được đăng nhập ngay và đưa vào dashboard.

### Qua API

```bash
# Đăng ký (trả về user + set session cookie)
curl -c cookies.txt -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"mat-khau-toi-thieu-8"}'

# Đăng nhập
curl -c cookies.txt -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"mat-khau-toi-thieu-8"}'

# Kiểm tra phiên hiện tại
curl -b cookies.txt http://localhost:8080/api/v1/auth/me

# Đăng xuất
curl -b cookies.txt -X POST http://localhost:8080/api/v1/auth/logout
```

Phiên đăng nhập là **cookie httpOnly, SameSite=Lax**, lưu trong Redis, thời hạn
30 phút. Bạn không cần (và không thể) lấy token thủ công cho các thao tác web.

> **Giới hạn tần suất:** đăng ký/đăng nhập tối đa 10 lần/phút/IP. Vượt quá sẽ
> nhận mã `429` kèm header `Retry-After`.

---

## 3. Xin cấp port (resource request)

Trước khi tạo tunnel loại PORT, bạn cần một **allocation** (port công khai).

### Qua giao diện web

1. Vào trang **Overview** hoặc **Requests**, tìm card **Resource requests**.
2. Điền form:
   - **node**: chọn node gateway (ví dụ `VN-01`).
   - **preferred port (optional)**: port mong muốn, ví dụ `20050`. Để trống
     nếu chấp nhận port bất kỳ trong dải của node (được cấp nhanh hơn).
   - **days**: số ngày sử dụng, từ 1 đến 365 (mặc định 30).
   - **purpose**: mô tả ngắn mục đích sử dụng — giúp admin duyệt nhanh hơn.
3. Bấm **Request port** → request chuyển trạng thái `PENDING`.
4. Chờ admin duyệt. Khi được duyệt, port xuất hiện trong card **My ports**
   kèm hạn dùng.

### Qua API

```bash
# Lấy danh sách node (để biết nodeId)
curl -b cookies.txt http://localhost:8080/api/v1/nodes

# Gửi yêu cầu
curl -b cookies.txt -X POST http://localhost:8080/api/v1/resource-requests \
  -H 'Content-Type: application/json' \
  -d '{
    "nodeId": "<node-id>",
    "protocol": "TCP",
    "preferredPort": 20050,
    "durationDays": 30,
    "purpose": "SSH vào máy dev"
  }'

# Xem các request của mình
curl -b cookies.txt http://localhost:8080/api/v1/resource-requests

# Xem port đã được cấp
curl -b cookies.txt http://localhost:8080/api/v1/ports
```

Quy tắc:

- Node phải hỗ trợ protocol bạn chọn (hiện tại các node hỗ trợ `TCP`, `UDP`;
  giao diện web đang gửi `TCP`).
- `durationDays` phải từ 1–365.
- Giới hạn: **30 request/giờ** cho mỗi tài khoản.
- Nếu port bạn "ưa thích" đã có người dùng, hệ thống tự chọn port khác còn
  trống trong dải của node.

### Vòng đời của port sau khi được cấp

```text
Còn hạn ──(còn ≤5 ngày)──► EXPIRING (cảnh báo trên dashboard)
        ──(hết hạn)──────► tunnel tự STOP, port giữ 3 ngày ân hạn
        ──(hết ân hạn)──► port RELEASED, trả về cho người khác
```

Nếu vẫn cần dùng sau khi hết hạn: gửi resource request mới.

---

## 4. Cài đặt và chạy agent (duox-agent)

Agent chạy trên **máy chứa dịch vụ** của bạn (không phải máy gateway). Nó:

- sinh một cặp khóa **Ed25519** ở lần chạy đầu — khóa công khai là danh tính
  bền vững của thiết bị;
- đăng ký thiết bị với tài khoản của bạn (chỉ lần đầu cần email/password);
- nhận cấu hình tunnel từ Control Plane, render `frpc.toml` và giám sát tiến
  trình `frpc`;
- gửi heartbeat mỗi 15 giây để hệ thống biết thiết bị còn sống.

### 4.1. Chuẩn bị

1. **frpc** phiên bản **v0.71.0** (hệ thống đang pin bản này): tải từ
   [github.com/fatedier/frp/releases](https://github.com/fatedier/frp/releases).
2. **duox-agent**: tải bản release cho nền tảng của bạn (linux/windows/darwin,
   amd64/arm64), hoặc tự build:

   ```bash
   cd dtunnel-agent
   go build -o bin/duox-agent ./cmd/duox-agent
   ```

### 4.2. Chạy lần đầu (đăng ký thiết bị)

```bash
./duox-agent \
  --server http://localhost:8080 \
  --email you@example.com \
  --password 'mat-khau-cua-ban' \
  --frpc /usr/local/bin/frpc
```

Lần chạy đầu, agent sẽ:

1. Tạo khóa thiết bị và lưu tại:
   - Linux/macOS: `~/.config/duox-agent/identity.json`
   - Windows: `%APPDATA%\duox-agent\identity.json`
2. Gọi `POST /agent/v1/register` với email/password của bạn → nhận `agentId`
   + token thiết bị, lưu vào `state.json` cùng thư mục.
3. Chuyển sang chế độ chờ: log `"agent awaiting SUPERADMIN approval"`.

> Từ lần chạy sau **không cần** `--email/--password` nữa — agent dùng token
> đã lưu:
>
> ```bash
> ./duox-agent --server http://localhost:8080 --frpc /usr/local/bin/frpc
> ```

### 4.3. Chờ admin duyệt thiết bị

Agent mới đăng ký ở trạng thái `PENDING`. Khi admin bấm **Approve** (xem
[admin-guide](admin-guide.md)), agent chuyển `ONLINE` và bắt đầu nhận cấu
hình. Bạn có thể theo dõi trạng thái trong trang **Agents** trên dashboard.

### 4.4. Toàn bộ tham số của agent

| Cờ | Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|---|
| `--server` | `DUOX_SERVER` | `http://localhost:8080` | URL Control Plane |
| `--grpc` | `DUOX_GRPC` | _(rỗng = chỉ REST)_ | Bật kênh gRPC, ví dụ `--grpc localhost:9091`. Khuyến nghị bật: nhận cấu hình **push** ngay lập tức và bị thu hồi trong **dưới 1 giây** |
| `--email` | — | — | Email tài khoản, chỉ cần khi đăng ký lần đầu |
| `--password` | — | — | Mật khẩu tài khoản, chỉ cần khi đăng ký lần đầu |
| `--frpc` | `DUOX_FRPC` | `frpc` (trong PATH) | Đường dẫn tới executable frpc |
| `--heartbeat` | — | `15s` | Chu kỳ heartbeat (15–20s là hợp lý) |
| `--poll` | — | `10s` | Chu kỳ hỏi phiên bản cấu hình (REST backstop) |

Ví dụ đầy đủ với gRPC:

```bash
./duox-agent \
  --server http://control-plane.example.com:8080 \
  --grpc control-plane.example.com:9091 \
  --frpc /usr/local/bin/frpc
```

Khi kênh gRPC kết nối, agent tự ngừng gửi heartbeat qua REST (tránh báo kép);
khi kênh gRPC đứt, REST tự động gánh lại — bạn không cần làm gì.

### 4.5. Chạy agent như service (Linux, khuyến nghị)

```ini
# /etc/systemd/system/duox-agent.service
[Unit]
Description=duox tunnel agent
After=network-online.target

[Service]
User=youruser
ExecStart=/usr/local/bin/duox-agent --server http://control-plane:8080 --grpc control-plane:9091 --frpc /usr/local/bin/frpc
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl enable --now duox-agent
```

### 4.6. Bảo mật phía agent

- **Không chia sẻ** thư mục `~/.config/duox-agent/` — `identity.json` chứa
  khóa riêng tư, `state.json` chứa token thiết bị.
- Token thiết bị có thời hạn 24 giờ và chỉ dùng được cho **chính thiết bị đã
  đăng ký**; lộ token cũng không chiếm được port của người khác (mọi NewProxy
  đều được máy chủ kiểm tra lại quyền sở hữu).
- Nếu mất máy hoặc nghi lộ khóa: báo admin **Revoke** agent đó, rồi đăng ký
  thiết bị mới.

---

## 5. Tạo và chạy tunnel

Điều kiện tiên quyết:

- Có allocation còn hạn (tunnel PORT) — mục 3; hoặc node có cấu hình HTTP edge
  (tunnel HTTP).
- Có agent **đã được duyệt** (không ở trạng thái `REVOKED`) — mục 4.

### 5.1. Tunnel PORT (TCP/UDP) qua giao diện web

1. Vào trang **Overview** hoặc **Tunnels**, card **Tunnels**.
2. Ở khối **New tunnel**, chọn chế độ **Port (TCP/UDP)**.
3. Điền:
   - **Service**: chọn nhanh theo mục đích — SSH (22), HTTP (80), HTTPS (443),
     RDP (3389), MySQL (3306), PostgreSQL (5432) hoặc **Custom TCP**. Chọn
     service sẽ tự điền target port gợi ý.
   - **Allocation (public port)**: chọn port bạn đã được cấp.
   - **Agent (your device)**: chọn thiết bị sẽ chạy tunnel.
   - **Name**: tên gợi nhớ, ví dụ `ssh-dev`.
   - **Target host**: địa chỉ dịch vụ nhìn từ máy chạy agent, thường là
     `127.0.0.1`.
   - **Target port**: port dịch vụ cục bộ, ví dụ `22`.
4. Bấm **Create tunnel** → tunnel ở trạng thái `CONFIGURED`.
5. Bấm **Start** → trạng thái `STARTING`, agent nhận cấu hình và chạy frpc.
   Khi frps xác nhận kết nối, tunnel chuyển **ACTIVE**.
6. Cột **Public** hiện endpoint, ví dụ `203.0.113.10:20050` — bấm nút copy để
   dùng ngay:

   ```bash
   ssh -p 20050 user@203.0.113.10
   ```

### 5.2. Tunnel HTTP (domain) qua giao diện web

1. Trong card **Tunnels**, chuyển chế độ **Domain (HTTP)**.
2. Điền:
   - **Node (HTTP edge)**: chỉ hiện các node đã bật vhost HTTP.
   - **Public domain**: domain bạn sở hữu, ví dụ `app.example.com`. Domain
     được chuẩn hóa chữ thường và **phải duy nhất trên toàn hệ thống**.
   - **Agent**, **Name**, **Target host**, **Target port** như trên (ví dụ
     web app local ở `127.0.0.1:8000`).
3. **Create tunnel** → **Start**.
4. Trỏ DNS của domain về địa chỉ edge (Caddy) của hệ thống. Với môi trường
   local/dev, thêm vào `/etc/hosts`:

   ```text
   127.0.0.1 app.example.com
   ```

   rồi truy cập qua edge: `curl -H 'Host: app.example.com' http://localhost:8090`
   hoặc `http://app.example.com:8090`.
5. Trong production (DNS thật), Caddy tự cấp chứng chỉ TLS cho domain.

> Tunnel HTTP **không cần xin port** — nó chạy trên port vhost dùng chung của
> node và được phân biệt bằng Host header. Plugin phía máy chủ kiểm tra mỗi
> kết nối: domain khai báo phải **khớp chính xác** domain đã đăng ký của tunnel.

### 5.3. Tạo tunnel qua API

```bash
# Tunnel PORT
curl -b cookies.txt -X POST http://localhost:8080/api/v1/tunnels \
  -H 'Content-Type: application/json' \
  -d '{
    "allocationId": "<allocation-id>",
    "agentId": "<agent-id>",
    "name": "ssh-dev",
    "targetHost": "127.0.0.1",
    "targetPort": 22,
    "bandwidthLimitMbps": 50,
    "maxConnections": 100
  }'

# Tunnel HTTP
curl -b cookies.txt -X POST http://localhost:8080/api/v1/tunnels/http \
  -H 'Content-Type: application/json' \
  -d '{
    "nodeId": "<node-id>",
    "agentId": "<agent-id>",
    "name": "web",
    "domain": "app.example.com",
    "targetHost": "127.0.0.1",
    "targetPort": 8000
  }'

# Danh sách tunnel của bạn
curl -b cookies.txt http://localhost:8080/api/v1/tunnels

# Start / Stop / Delete
curl -b cookies.txt -X POST http://localhost:8080/api/v1/tunnels/<id>/start
curl -b cookies.txt -X POST http://localhost:8080/api/v1/tunnels/<id>/stop
curl -b cookies.txt -X DELETE http://localhost:8080/api/v1/tunnels/<id>
```

Quy tắc cần nhớ:

- Chỉ dùng được allocation/agent **của chính bạn**.
- Allocation phải còn hạn tại thời điểm tạo.
- **Start** chỉ hợp lệ từ `CONFIGURED`, `STOPPED`, `ERROR`.
- **Stop** chỉ hợp lệ từ `STARTING`, `ACTIVE`.
- **Delete** yêu cầu tunnel đã dừng (không đang `ACTIVE`/`STARTING`).
  Xóa tunnel **giữ lại** allocation — bạn có thể tạo tunnel khác trên cùng port.

### 5.4. Giới hạn băng thông

Khi tạo tunnel (qua API) có thể đặt `bandwidthLimitMbps`. Agent chuyển thành
`transport.bandwidthLimit` trong `frpc.toml` để frpc tự giới hạn. Giao diện
web hiện chưa có ô nhập này — dùng API nếu cần.

---

## 6. Theo dõi lưu lượng (usage)

Lưu lượng được đo **phía máy chủ** (Control Plane đọc bộ đếm của frps admin
API), không dựa vào số liệu agent báo về — nên không thể khai gian.

### Trên dashboard

- Cột **Usage** trong bảng Tunnels hiện tổng ↓tải xuống / ↑tải lên.
- Bấm mũi tên **▸** đầu dòng để mở biểu đồ lưu lượng **30 ngày gần nhất**
  (Recharts).

### Qua API

```bash
# Tổng lưu lượng
curl -b cookies.txt http://localhost:8080/api/v1/tunnels/<id>/usage
# {"tunnelId":"...","bytesIn":123456,"bytesOut":789012}

# Lịch sử theo ngày (1–365 ngày, mặc định 30)
curl -b cookies.txt "http://localhost:8080/api/v1/tunnels/<id>/usage/history?days=30"
```

---

## 7. Các trang dashboard

| Trang | Nội dung |
|---|---|
| **Overview** | Dải KPI (tổng tunnel, active, lỗi, agent, node, port của bạn) + Tunnels + My ports + Requests |
| **Tunnels** | Tạo/quản lý tunnel + danh sách port |
| **Requests** | Gửi yêu cầu port + theo dõi trạng thái duyệt |
| **Agents** | Lệnh cài agent + danh sách thiết bị của bạn |
| **Admin** | Chỉ SUPERADMIN nhìn thấy — không dành cho USER |

---

## 8. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Agent log `"not registered: run with --register --email --password first"` | Chạy lần đầu nhưng thiếu `--email/--password`, hoặc `state.json` bị xóa | Chạy lại với đủ thông tin đăng ký |
| Agent log `"agent awaiting SUPERADMIN approval"` mãi | Agent đang `PENDING` | Liên hệ admin duyệt thiết bị |
| Tunnel `STARTING` lâu không `ACTIVE` | frpc chưa kết nối được node; firewall chặn port 7000; agent chưa online | Kiểm tra log agent/frpc; đảm bảo máy agent ra được Internet tới node |
| Tunnel chuyển `ERROR` | frpc bị đóng kết nối bất thường, hoặc agent mất mạng | Hệ thống **tự khôi phục** khi agent online lại; nếu không, bấm Start thủ công |
| Tunnel `EXPIRING` | Allocation còn ≤ 5 ngày | Gửi resource request mới trước khi hết hạn |
| `429 rate limit exceeded` | Vượt giới hạn tần suất | Chờ hết thời gian trong header `Retry-After` |
| `403 forbidden` khi tạo tunnel | Dùng nhầm allocation/agent của tài khoản khác, hoặc agent đã bị revoke | Kiểm tra lại tài nguyên thuộc tài khoản của bạn |
| `409 conflict: domain already in use` | Domain đã được tunnel khác đăng ký | Chọn domain khác hoặc liên hệ admin |
| Endpoint HTTP trả về trang lạ / 404 | DNS chưa trỏ về edge, hoặc Host header sai | Kiểm tra DNS/`/etc/hosts` và Host header |
| Agent `OFFLINE` trên dashboard | Mất heartbeat > 60 giây (tắt máy, mất mạng) | Khởi động lại agent; tunnel tự phục hồi khi agent online |

### Kiểm tra nhanh bằng API

```bash
curl -b cookies.txt http://localhost:8080/api/v1/agents      # trạng thái agent
curl -b cookies.txt http://localhost:8080/api/v1/tunnels     # trạng thái tunnel
curl -b cookies.txt http://localhost:8080/api/v1/ports       # port + hạn dùng
```

### Kiểm tra node trước khi chọn

```bash
curl -b cookies.txt -X POST http://localhost:8080/api/v1/nodes/<node-id>/ping
# {"reachable":true,"latencyMs":42}
```

---

## 9. Câu hỏi thường gặp

**Tôi có thể chạy nhiều tunnel trên một agent không?**
Có. Một agent nhận toàn bộ tunnel của bạn trong cùng một cấu hình frpc. Mỗi
tunnel là một proxy riêng.

**Một port cấp cho tôi dùng được mấy tunnel?**
Mỗi allocation gắn với một tunnel đang chạy tại một thời điểm (port là duy
nhất trên node). Muốn nhiều tunnel song song, xin thêm allocation.

**Xóa tunnel có mất port không?**
Không. Allocation được giữ nguyên đến hết hạn; bạn tạo tunnel khác trên cùng
allocation được.

**Đổi máy chạy dịch vụ thì sao?**
Cài agent trên máy mới (đăng ký thiết bị mới, admin duyệt), rồi sửa tunnel
chọn agent mới — hoặc tạo tunnel mới. Agent cũ nên được admin revoke.

**Token/key của agent để ở đâu, có cần backup không?**
`~/.config/duox-agent/` (hoặc `%APPDATA%\duox-agent\`). Backup thư mục này
giúp chuyển agent sang máy khác giữ nguyên danh tính — nhưng chỉ làm vậy khi
bạn tin tưởng nơi lưu trữ; cách an toàn nhất khi chuyển máy là đăng ký thiết
bị mới.

**Vì sao tôi không tự chọn port tùy ý?**
Node chỉ mở dải `allowPorts` (ví dụ 20000–20100). Bạn có thể đề xuất port
trong dải đó; nếu trùng, hệ thống cấp port khác.

---

## 10. Tóm tắt API cho USER

| Method + Path | Mô tả |
|---|---|
| `POST /api/v1/auth/register` | Đăng ký tài khoản |
| `POST /api/v1/auth/login` | Đăng nhập |
| `POST /api/v1/auth/logout` | Đăng xuất |
| `GET /api/v1/auth/me` | Thông tin phiên hiện tại |
| `GET /api/v1/nodes` | Danh sách node |
| `POST /api/v1/nodes/{id}/ping` | Đo độ trễ tới node |
| `POST /api/v1/resource-requests` | Gửi yêu cầu cấp port |
| `GET /api/v1/resource-requests` | Danh sách request của bạn |
| `GET /api/v1/ports` | Port của bạn + hạn dùng |
| `GET /api/v1/agents` | Agent của bạn |
| `POST /api/v1/tunnels` | Tạo tunnel PORT |
| `POST /api/v1/tunnels/http` | Tạo tunnel HTTP |
| `GET /api/v1/tunnels` | Danh sách tunnel |
| `POST /api/v1/tunnels/{id}/start` | Start |
| `POST /api/v1/tunnels/{id}/stop` | Stop |
| `DELETE /api/v1/tunnels/{id}` | Xóa (phải stop trước) |
| `GET /api/v1/tunnels/{id}/usage` | Tổng lưu lượng |
| `GET /api/v1/tunnels/{id}/usage/history?days=N` | Lịch sử lưu lượng |

Tài liệu API tương tác: `http://<control-plane>:8080/swagger-ui.html`.
