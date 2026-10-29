# Kids Fun Zone 🏁

An Android game app for kids aged 4 and up. Every instruction is spoken
aloud, so children who can't read yet can still play. The app has no ads,
asks for no internet permission and collects no data.

## 🏁 Vehicle Racing (main game)

A top-down, three-lane race against three other vehicles.

**10 vehicles.** Each one has its own look, speed and handling:

| Vehicle | Notes |
|---|---|
| 🏎️ Race Car | Fastest and nimble |
| 🚗 Car | All-rounder |
| 🚓 Police Car | Flashing light bar and siren button |
| 🏍️ Motorbike | Quick, with a rider in a helmet |
| 🚌 Bus | Long and heavy, so bumps barely slow it |
| 🚚 Truck | Cab plus trailer, deep horn |
| 🚒 Fire Truck | Roof ladder, flashing lights, siren |
| 🚜 JCB Digger | Front loader and back-hoe, flashing beacon; **off-road** |
| 🚜 Tractor | Huge rear wheels; **off-road** |
| 🛻 Monster Truck | Giant wheels, flames; **off-road** |

Off-road vehicles drive straight through mud and over rocks.

**5 tracks:** City Streets, Highway, Farm Road, Building Site and Snowy
Mountain. Each has its own scenery and hazards: cones, puddles, oil, mud,
rocks and slippery ice. The snow track also has less grip.

**What makes it feel real**
- Vehicles are sized in real metres (a bus really is much longer than a bike).
- Tyre treads turn and the body tilts into corners.
- The engine sound is synthesized live and its pitch rises with speed. There's also a horn or siren button.
- There's a speedometer in km/h, a live race position (1st/2nd…) and a progress bar.
- Dust trails show on dirt roads, boost flames on boost pads, and the screen shakes on bumps.
- You can't drive through the other racers: you have to steer around them to overtake.

**Made for small hands:** the vehicle accelerates by itself and follows
the child's finger left and right. There's a spoken 3-2-1-Go countdown,
and gentle rubber-banding keeps every race close.

## 🎓 Learning games

| Game | What kids practise |
|---|---|
| 🔢 Count It! | Counting 1–10 |
| 🎨 Colors & Shapes | Naming colours and shapes |
| 🐶 Memory Match | Memory and concentration |
| 🔤 ABC Fun | Letters and their starting sounds |
| ➕ Add It Up | Picture addition |
| 🎈 Pop the Balloons | Hand–eye coordination and colours |
| 🖍️ Paint Time | Free finger painting |

Stars earned in every game add up on the home screen.

## Build & install

- **Android Studio:** open this folder and press ▶ Run.
- **Command line:** `./gradlew assembleDebug`. The APK ends up in `app/build/outputs/apk/debug/`.
- **No setup needed:** every push runs the *Build APK* GitHub Action. Download the APK from the run's *Artifacts* section and install it on the phone.

Requires Android 8.0 (API 26) or newer. All graphics are drawn in code and
all sounds are synthesized, so there are no image or audio assets.

> Note: "JCB" is a trademark of J.C. Bamford Excavators. If you publish to the
> Play Store, consider renaming the vehicle to "Digger" in `VehicleType.kt`.
