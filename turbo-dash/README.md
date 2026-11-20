# 🏎️ Turbo Dash

A never-ending car racing game for kids. It runs in the phone's browser and can be
installed on an Android home screen like a normal app. It also works offline.

## How to play
- **Touch and slide** your finger left or right to steer. On a computer, use the arrow keys.
- Dodge the traffic. You start with 3 ❤️ hearts and lose one each time you crash.
- Grab ⭐ coins (10 points each), ❤️ hearts (an extra life, up to 5) and 🛡️ shields
  (6 seconds of bumping cars out of the way).
- The road never ends. The game slowly speeds up, and the scenery changes every 1500 m:
  Green Hills → Sunny Desert → Snowy Land → Candy Town → Night City → Beach Road, and then round again.
- Your best score is saved on the phone.

## Put it on an Android phone
1. Host this folder on any HTTPS site. The easiest way is **GitHub Pages**: in the repo,
   go to *Settings → Pages* and deploy from the branch. The game will then be at
   `https://<user>.github.io/<repo>/turbo-dash/`.
2. Open that link in **Chrome** on the Android phone.
3. Tap the **⋮** menu, then **Add to Home screen** or **Install app**.
4. Turbo Dash now has its own icon and opens fullscreen, even with no internet.

## Run it on a computer
```
cd turbo-dash && python3 -m http.server 8000
```
Then open http://localhost:8000.
