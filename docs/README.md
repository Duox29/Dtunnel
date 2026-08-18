# Tài liệu hướng dẫn sử dụng dtunnel

dtunnel là nền tảng quản lý tunnel: **Control Plane** quyết định ai được phép
tunnel, ở đâu, khi nào và theo chính sách nào; FRP (frps/frpc) chỉ thực thi
trạng thái tunnel đã được phê duyệt.

Bộ tài liệu này chia theo vai trò:

| Tài liệu | Dành cho | Nội dung chính |
|---|---|---|
| [user-guide.md](user-guide.md) | **Người dùng (USER)** | Đăng ký tài khoản, xin cấp port, cài agent, tạo/chạy tunnel (TCP/UDP theo port và HTTP theo domain), theo dõi lưu lượng, xử lý sự cố |
| [admin-guide.md](admin-guide.md) | **Quản trị viên (SUPERADMIN)** | Triển khai hệ thống, đăng ký node, seed port, duyệt request/agent, thu hồi quyền, audit trail, observability, vận hành job nền |

## Kiến trúc tóm tắt

```text
Web UI (React) ──► Control Plane (Spring Boot :8080, gRPC :9091)
                        │        │
                    Postgres    Redis
                    (sự thật)   (session, rate limit, token)
                        │
        ┌───────────────┼────────────────┐
        ▼               ▼                ▼
   Node gateway     Node gateway     ...
   frps + duox-node-agent
        ▲
        │ frpc (được giám sát bởi duox-agent)
   Máy người dùng
```

- **Control Plane**: xét duyệt, cấp port, đẩy cấu hình desired-state, ủy quyền
  thời gian thực mọi Login/NewProxy của frps qua server-plugin.
- **duox-agent** (phía người dùng): đăng ký thiết bị bằng khóa Ed25519, nhận
  cấu hình, chạy frpc, gửi heartbeat.
- **duox-node-agent** (phía gateway): báo cáo sức khỏe/tài nguyên của node,
  xác thực bằng node token riêng.
- **frps**: triển khai một lần mỗi node với dải `allowPorts`; không cần sửa
  cấu hình khi thêm tunnel — plugin callback hỏi Control Plane từng kết nối.

## Luồng nghiệp vụ tổng quan

1. User đăng ký tài khoản → gửi **resource request** (node, protocol, port mong
   muốn, số ngày).
2. SUPERADMIN duyệt → port được cấp (allocation có hạn dùng).
3. User chạy **duox-agent** trên máy chứa dịch vụ → agent đăng ký thiết bị →
   SUPERADMIN duyệt agent.
4. User tạo **tunnel** từ allocation (hoặc tunnel HTTP theo domain) → bấm Start.
5. Agent nhận desired-state, render `frpc.toml`, chạy frpc.
6. frps gọi plugin về Control Plane cho mỗi Login/NewProxy — được duyệt thì
   tunnel chuyển **ACTIVE**, lưu lượng chạy qua.

## Tài liệu liên quan trong repo

- [README.md](../README.md) — tổng quan kỹ thuật, quick start, build/test.
- [detail.md](../detail.md) — đặc tả kỹ thuật đầy đủ (spec v0.2).
- [AUDIT.md](../AUDIT.md) — đối chiếu triển khai với đặc tả.
- Swagger API: `http://<control-plane>:8080/swagger-ui.html`.
