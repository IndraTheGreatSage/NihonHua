# NihonHua task tracker

- [x] Analisis backend (main.py/server.js) dan Android client (ApiService/ViewModel)
- [x] Temukan root cause: BASE_URL hardcode mengarah ke endpoint yang mungkin tidak match
- [x] Buat config wrapper: `ApiConfig.kt`
- [x] Ganti ApiService BASE_URL -> `ApiConfig.baseUrl`
- [ ] (Belum divalidasi) Pastikan endpoint `https://server.fromscratch.web.id/server/43312601/api/scrape` benar-benar ada dan mengembalikan JSON
- [ ] Tes di device/emulator: harus minimal dapat item dari LOCAL_ANIMES

