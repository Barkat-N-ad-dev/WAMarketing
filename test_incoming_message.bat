@echo off
title WA Marketing - Test Inbound Customer Message
echo ==============================================================================
echo        WA MARKETING - SIMULATE INCOMING WHATSAPP CUSTOMER MESSAGE
echo ==============================================================================
echo.
set /p SENDER_PHONE="Enter Customer Phone Number (default: +923005551234): "
if "%SENDER_PHONE%"=="" set SENDER_PHONE=+923005551234

set /p MESSAGE_TEXT="Enter Customer Message (default: Hello! Interested in your brochure): "
if "%MESSAGE_TEXT%"=="" set MESSAGE_TEXT=Hello! Interested in your brochure

echo.
echo Dispatching simulated incoming WhatsApp message to backend on port 8005...
powershell -Command "$body = @{from_phone='%SENDER_PHONE%'; body='%MESSAGE_TEXT%'; sender_name='Verified Customer'} | ConvertTo-Json; try { $res = Invoke-RestMethod -Uri 'http://127.0.0.1:8005/api/v1/inbox/simulate-inbound' -Method Post -ContentType 'application/json' -Body $body; Write-Host 'SUCCESS: Message received in backend!' -ForegroundColor Green; Write-Host ($res | ConvertTo-Json) } catch { Write-Host 'ERROR:' $_.Exception.Message -ForegroundColor Red }"
echo.
echo ==============================================================================
echo Check your Android App Inbox now - the new customer conversation will appear!
echo ==============================================================================
pause
