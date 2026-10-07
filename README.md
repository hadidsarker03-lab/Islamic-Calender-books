# ইসলামিক ক্যালেন্ডার – Android (নেটিভ)

## APK বানানোর দুটি উপায়
1. **Android Studio**: এই ফোল্ডারটি Open করুন → Sync শেষ হলে `Build ▸ Build Bundle(s)/APK(s) ▸ Build APK(s)`।
   APK পাবেন `app/build/outputs/apk/debug/app-debug.apk`। ফোনে কপি করে ইনস্টল করুন
   (ফোন সেটিংসে "Install unknown apps" অনুমতি দিতে হতে পারে)।
2. **GitHub Actions (কম্পিউটার ছাড়াই)**: ফোল্ডারটি একটি GitHub রিপোজিটরিতে আপলোড করুন →
   Actions ট্যাব → "Build APK" → শেষে Artifacts থেকে `islamic-calendar-apk` ডাউনলোড করুন।

## আসল আজানের শব্দ
আজানের অডিও `app/src/main/res/raw/adhan.mp3` নামে রাখুন (ফোল্ডার না থাকলে বানান)।
না রাখলে ফোনের ডিফল্ট নোটিফিকেশন সাউন্ড বাজবে। নতুন সাউন্ড পেতে অ্যাপ আনইনস্টল করে আবার ইনস্টল করুন।

## ওয়েব অংশ
UI আছে `app/src/main/assets/www/index.html` ফাইলে। এটি এডিট করলেই অ্যাপে পরিবর্তন আসবে।
