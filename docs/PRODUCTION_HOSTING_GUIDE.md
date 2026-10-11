# WA Marketing — Complete Two-Way Communication & Cloud Hosting Guide

**Developer:** Barkat Ali ([Portfolio](https://barkatai.ihsanraza.com/#about))  
**Project:** WA Marketing Platform (Android, iOS, Web & Backend)

---

## 1. Why Incoming Messages Require Public Hosting / Webhook Setup

WhatsApp two-way communication consists of two distinct data directions:

1. **Outgoing Messages (Working Out of the Box):**  
   Your phone or local computer sends HTTP requests outward to Meta's Cloud API (`https://graph.facebook.com/...`). This works from any Wi-Fi or mobile network without special setup.

2. **Incoming Customer Messages (Requires Public Webhook):**  
   When a customer replies to your WhatsApp Business number, **Meta's servers in the cloud initiate an HTTP POST request** to notify your application. Meta cannot reach private local IP addresses like `192.168.255.37` or `127.0.0.1`. Meta requires an **HTTPS URL** with a valid SSL certificate.

---

## 2. Immediate Testing (No Server Needed - 2 Minutes)

You can immediately test real two-way communication from your computer using a free secure tunnel:

### Step 1: Start the Backend & Tunnel
1. Run `run_backend.bat` (or `.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8005`).
2. Double-click `start_webhook_tunnel.bat` in the project root.
3. Select **Option 1 (Cloudflare Tunnel)** or **Option 2 (SSH Tunnel)**.
4. Copy the public HTTPS URL shown in the terminal (e.g., `https://xyz.trycloudflare.com` or `https://xyz.lhr.life`).

### Step 2: Configure Meta Developer Dashboard
1. Go to [developers.facebook.com](https://developers.facebook.com) -> **My Apps** -> Select your WhatsApp App.
2. In the left sidebar, click **WhatsApp** -> **Configuration**.
3. Under the **Webhook** section, click **Edit**:
   * **Callback URL:** `https://YOUR-TUNNEL-URL/api/v1/webhooks/whatsapp`
   * **Verify Token:** `wamarketing_secure_verify_token`
4. Click **Verify and Save**.
5. Under **Webhook fields**, click **Manage** and subscribe to **`messages`** and **`message_deliveries`**.

### Step 3: Test from Customer Phone
1. Send a WhatsApp message from any personal phone to your WhatsApp Business number.
2. Open the **WA Marketing Android App**.
3. Open the **Inbox** tab:
   * The new customer conversation appears instantly with an unread badge.
   * You can open the conversation and reply directly from the app!

---

## 3. Permanent 24/7 Cloud Hosting Options

For production where clients and customers use the application 24/7 without keeping your computer turned on, host the backend in the cloud.

### Option A: Free / Low-Cost Cloud (Render.com or Railway.app)
**Recommended for fast, maintenance-free deployment.**

1. Push your project or `backend/` folder to a GitHub repository.
2. Sign up at [Render.com](https://render.com) (or [Railway.app](https://railway.app)).
3. Click **New +** -> **Web Service** -> Connect your GitHub repo.
4. Configure the build settings:
   * **Root Directory:** `backend`
   * **Environment:** Python 3
   * **Build Command:** `pip install -r requirements.txt`
   * **Start Command:** `uvicorn app.main:app --host 0.0.0.0 --port $PORT`
5. Under **Environment Variables**, add:
   * `WHATSAPP_PHONE_NUMBER_ID` = your phone number ID
   * `WHATSAPP_ACCESS_TOKEN` = your permanent system user token
   * `WHATSAPP_WEBHOOK_VERIFY_TOKEN` = `wamarketing_secure_verify_token`
   * `GEMINI_API_KEY` = your Gemini API key
6. Click **Deploy Web Service**.
7. Render will provide a permanent free HTTPS URL (e.g., `https://wamarketing-api.onrender.com`).
8. Paste `https://wamarketing-api.onrender.com/api/v1/webhooks/whatsapp` into your Meta Webhook settings!
9. In the Android app **Settings -> Server Configuration**, set:  
   `https://wamarketing-api.onrender.com`

---

### Option B: Dedicated Cloud VPS (Ubuntu 22.04 / 24.04)
**Recommended for full enterprise control, custom domains, and highest performance.**
*(DigitalOcean, Hetzner, AWS Lightsail, Linode, Contabo: $4 - $6/month)*

1. Create an Ubuntu VPS instance.
2. Point your custom domain or subdomain (e.g., `api.yourdomain.com`) to your server IP.
3. SSH into the server:
   ```bash
   ssh root@your-server-ip
   ```
4. Clone and run using Docker:
   ```bash
   git clone <YOUR-REPO-URL> wamarketing
   cd wamarketing/docker
   docker compose up -d
   ```
5. Install Nginx and Let's Encrypt SSL:
   ```bash
   sudo apt update && sudo apt install -y nginx certbot python3-certbot-nginx
   sudo certbot --nginx -d api.yourdomain.com
   ```
6. Configure Nginx reverse proxy in `/etc/nginx/sites-available/wamarketing`:
   ```nginx
   server {
       server_name api.yourdomain.com;

       location / {
           proxy_pass http://127.0.0.1:8001;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;

           # WebSocket support
           proxy_http_version 1.1;
           proxy_set_header Upgrade $http_upgrade;
           proxy_set_header Connection "upgrade";
       }
   }
   ```
7. Enable site and reload:
   ```bash
   sudo ln -s /etc/nginx/sites-available/wamarketing /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl reload nginx
   ```
8. Set Callback URL in Meta:  
   `https://api.yourdomain.com/api/v1/webhooks/whatsapp`

---

## 4. Verification Checklist

- [x] Backend running on port 8005 (or 443 in production)
- [x] Gemini API model updated to `gemini-flash-lite-latest` (Tested: 200 OK)
- [x] Conversation 404 issue resolved (gracefully handles uninitiated contact threads)
- [x] In-app simulation test verified (`test_incoming_message.bat`)
- [x] Meta Webhook verification handshake tested (`GET /api/v1/webhooks/whatsapp` returns challenge)
- [x] Meta Webhook message parser tested (`POST /api/v1/webhooks/whatsapp` creates contact + conversation + unread count)
- [x] Android APK compiled and ready: [wamarketing-debug.apk](file:///C:/Users/Barkat%20Ali/.gemini/antigravity/brain/56ac57dc-da92-4398-9b18-5d6fbb9b00d9/wamarketing-debug.apk)
