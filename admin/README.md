# 粮满满 · 运营后台

给平台运营人员在电脑上用的管理后台。Vue 3 + Vite 5 + Element Plus 2 + vue-router 4 + axios，纯前端，对接 `docs/api-v2.md` 第九节 `/api/admin/**`。

## 开发运行

需要 Node.js 18+。

```bash
cd admin
npm install
npm run dev
```

打开 <http://localhost:5174/admin/>。开发服务器把 `/api` 代理到 `http://127.0.0.1:8080`，先把后端跑起来（见根目录 README）。

## 构建

```bash
npm run build
```

产物在 `admin/dist`。所有资源路径都以 `/admin/` 开头（`vite.config.js` 里 `base: '/admin/'`），所以必须部署在域名的 `/admin/` 路径下。

## 生产部署（nginx）

把 `dist` 目录拷到服务器，例如 `/var/www/liangmanman-admin`，nginx 配置示例：

```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;

    ssl_certificate     /etc/nginx/ssl/api.example.com.pem;
    ssl_certificate_key /etc/nginx/ssl/api.example.com.key;

    client_max_body_size 10m;   # 图片上传

    # 运营后台静态文件
    location /admin/ {
        alias /var/www/liangmanman-admin/;
        index index.html;
        # 前端是 history 路由，刷新子页面时回落到 index.html
        try_files $uri $uri/ /admin/index.html;
    }

    # 带 hash 的静态资源可长期缓存
    location /admin/assets/ {
        alias /var/www/liangmanman-admin/assets/;
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 后端接口
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

`index.html` 不要设长缓存，发版后用户刷新即可拿到新版本。

## 约定

- 登录后 token 存在 `localStorage` 的 `lm_admin_token`，请求头带 `Authorization: Bearer <token>`。
- 接口返回 `code !== 0` 时弹出 `msg`；`code === 401` 自动清除登录状态并跳到登录页。
- 金额显示保留 2 位小数；时间把 `T` 换成空格；后端不输出的 null 字段显示为 `-`。
- 「管理员」菜单只有 `role = ADMIN` 能看到。

## 目录

```
src/
├── api/index.js          所有接口
├── utils/                request（axios 封装）、auth（登录态）、format（金额/时间）、dict（状态字典）、prompt（填原因/二次确认）
├── composables/          分页列表、分类缓存、区域列表
├── components/           分页、状态标签、缩略图、图片上传、筛选栏、趋势柱状图
├── layout/               左侧菜单 + 顶栏、改密码弹窗
├── router/index.js       路由与菜单定义
└── views/                各页面
```
