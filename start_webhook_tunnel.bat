@echo off
title WA Marketing - Meta Webhook Tunnel (Two-Way WhatsApp)
echo ==============================================================================
echo           WA MARKETING - META WHATSAPP WEBHOOK TUNNEL SETUP
echo ==============================================================================
echo.
echo For real customers to message your WhatsApp Business number and have their
echo replies appear in your app's Inbox, Meta requires a public HTTPS Webhook URL.
echo.
echo Current Local Backend: http://localhost:8005
echo Webhook Path: /api/v1/webhooks/whatsapp
echo Verify Token: wamarketing_secure_verify_token
echo.
echo ==============================================================================
echo SELECT YOUR TUNNEL METHOD:
echo [1] Cloudflare Tunnel (Recommended - Fast, Secure, No signup needed)
echo [2] Instant SSH Tunnel (Zero install - uses Windows built-in OpenSSH)
echo [3] LocalTunnel (Requires Node.js / npx)
echo ==============================================================================
set /p CHOICE="Enter choice [1, 2, or 3] (default 1): "
if "%CHOICE%"=="" set CHOICE=1

if "%CHOICE%"=="1" goto CLOUDFLARE
if "%CHOICE%"=="2" goto SSH_TUNNEL
if "%CHOICE%"=="3" goto LOCALTUNNEL

:CLOUDFLARE
echo.
echo Checking cloudflared binary...
if not exist "cloudflared.exe" (
    echo Downloading official Cloudflare Tunnel for Windows...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe', 'cloudflared.exe')"
)
echo Starting Cloudflare Tunnel on http://127.0.0.1:8005...
echo Look for the 'https://*.trycloudflare.com' URL in the output below!
echo.
.\cloudflared.exe tunnel --url http://127.0.0.1:8005
pause
exit /b

:SSH_TUNNEL
echo.
echo Connecting to localhost.run via built-in Windows OpenSSH...
echo Your public HTTPS URL will be printed on screen shortly:
echo.
ssh -R 80:localhost:8005 nokey@localhost.run
pause
exit /b

:LOCALTUNNEL
echo.
echo Starting localtunnel on port 8005...
npx -y localtunnel --port 8005
pause
exit /b
