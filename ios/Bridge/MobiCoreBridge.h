#import <Foundation/Foundation.h>
#import <CoreGraphics/CoreGraphics.h>

NS_ASSUME_NONNULL_BEGIN

/**
 Objective-C face of the emulator core.

 The core is plain Java translated by J2ObjC; this class is the only place that
 touches the generated types. Swift sees Foundation objects and a CGImage, so a
 change inside the core never ripples into the UI layer.

 Everything structured crosses as JSON, matching
 `com.mobicore.core.bridge.MobiCoreFacade`.
 */
@interface MobiCoreBridge : NSObject

/// Shared instance; the library and the running game are process-wide state.
@property (class, readonly) MobiCoreBridge *shared;

#pragma mark - Library

/// Creates the storage tree under `root` (normally Application Support).
- (NSString *)openAtPath:(NSString *)root;
@property (nonatomic, readonly) BOOL isOpen;
@property (nonatomic, readonly) NSString *storageRoot;

/// The whole library as JSON: games, embedded profiles, recent and favourites.
- (NSString *)libraryJSON;

/// Cover art bytes, or nil when the suite ships none.
- (nullable NSData *)artworkForSuite:(NSString *)suiteId;

/// Imports a suite. `descriptor` may be nil for a bare JAR.
- (NSString *)importJar:(NSData *)jar descriptor:(nullable NSData *)descriptor;

- (NSString *)uninstallSuite:(NSString *)suiteId keepData:(BOOL)keepData;

/// Imports everything the user picked, pairing descriptors with archives and
/// unpacking a zip of games. Every file is reported on separately.
- (NSString *)importMany:(NSArray<NSString *> *)names payloads:(NSArray<NSData *> *)payloads;

/// Renames a game as the library lists it; the manifest title is kept.
- (NSString *)renameSuite:(NSString *)suiteId title:(NSString *)title;

/// Puts the suite's own title back.
- (NSString *)resetTitleForSuite:(NSString *)suiteId;

/// Replaces the cover art. PNG only — anything else is refused.
- (NSString *)setArtwork:(NSData *)png forSuite:(NSString *)suiteId;

/// Puts the icon the suite ships back on the tile.
- (NSString *)resetArtworkForSuite:(NSString *)suiteId;

/// Re-runs the automatic setup for a game, discarding hand-set values.
- (NSString *)autoSetupForSuite:(NSString *)suiteId;

/// The library filtered and ordered by the core, so search behaves the same
/// on both platforms — marks ignored, renamed games found under either name.
- (NSString *)searchJSON:(NSString *)query sort:(int32_t)sort;

/// Remembers the order the library opens in.
- (NSString *)setLibrarySort:(int32_t)sort;

#pragma mark - Appearance

/// Settings that belong to the person: the theme, the library's sort order.
- (NSString *)appSettingsJSON;

/// 0 light, 1 dark, 2 follow the phone.
- (NSString *)setTheme:(int32_t)theme;

/// Light to dark to system and back.
- (NSString *)cycleTheme;

/// Whether the emulated handset's title and softkey bars are drawn dark.
- (void)setChromeDark:(BOOL)dark;

#pragma mark - Text entry

/// True while the game is showing a TextBox or a focused text field.
- (BOOL)isTextInputActive;

/// What that field holds now, so the keyboard opens on it.
- (NSString *)textInput;

/// Puts what the system keyboard produced into the field.
- (NSString *)setTextInput:(NSString *)value;

#pragma mark - Save states

/// Saves the running game where it stands, with a picture of the screen.
- (NSString *)saveState;

/// Starts a game and puts it back where its saved state left it. Distinct
/// from `resumeGame`, which merely unpauses a game already running.
- (NSString *)startGameResuming:(NSString *)suiteId;

- (BOOL)hasSaveStateForSuite:(NSString *)suiteId;

/// The screen the player left, as PNG bytes, or nil.
- (nullable NSData *)saveStateThumbnailForSuite:(NSString *)suiteId;

- (NSString *)deleteSaveStateForSuite:(NSString *)suiteId;

/// Saves the running game and then stops it: what leaving a game means.
- (NSString *)stopGameSaving;

#pragma mark - Profiles

- (NSString *)profileJSONForSuite:(NSString *)suiteId;
- (NSString *)updateProfileJSON:(NSString *)json;
- (NSString *)setInputPreset:(NSString *)preset forSuite:(NSString *)suiteId;
- (NSString *)toggleFavouriteForSuite:(NSString *)suiteId;

/** Portrait to landscape and back, remembered with the game. */
- (NSString *)toggleOrientationForSuite:(NSString *)suiteId;

/** Full keypad, arrows only, numbers only, hidden — and round again. */
- (NSString *)cycleKeypadLayoutForSuite:(NSString *)suiteId;

/** Saves a picture of what the running game is showing. */
- (NSString *)takeScreenshot;

/** Every MIDlet inside one suite, and which one the play button opens. */
- (NSString *)midletsJSONForSuite:(NSString *)suiteId;
- (NSString *)startGame:(NSString *)suiteId midlet:(NSString *)midletClass;

/** The whole library as one file, and back again. */
- (nullable NSData *)exportLibrary;
- (NSString *)importLibrary:(NSData *)archive;

/** Points one virtual button at a different key code. */
- (NSString *)setKeyMapping:(int32_t)keyCode forButton:(NSString *)button suite:(NSString *)suiteId;

/** Every key code a button can be pointed at, with its name. */
- (NSString *)keyChoicesJSON;

/** Auto-repeat for one button, in ms between presses; 0 switches it off. */
- (NSString *)setTurbo:(int32_t)intervalMs forButton:(NSString *)button suite:(NSString *)suiteId;

/** Takes back the last second or so of play. */
- (NSString *)rewindStep;
- (NSString *)rewindJSON;
- (NSString *)setRewindEnabled:(BOOL)enabled;

/** How fast the running game plays; steps through half, normal, 2x, 3x. */
- (NSString *)cycleSpeed;
- (NSString *)speedJSON;

/** Save slots: the emulator's own at zero, then the player's. */
- (NSString *)saveStateInSlot:(int32_t)slot;
- (NSString *)loadStateFromSlot:(int32_t)slot;
- (NSString *)saveStatesJSONForSuite:(NSString *)suiteId;
- (nullable NSData *)saveStateThumbnailForSuite:(NSString *)suiteId slot:(int32_t)slot;
- (NSString *)deleteSaveStateForSuite:(NSString *)suiteId slot:(int32_t)slot;

/** Named settings, saved once and applied to any game. */
- (NSString *)presetsJSON;
- (NSString *)savePreset:(NSString *)name fromSuite:(NSString *)suiteId;
- (NSString *)applyPreset:(NSString *)name toSuite:(NSString *)suiteId;
- (NSString *)deletePreset:(NSString *)name;
- (NSString *)setDefaultPreset:(NSString *)name;

/** Every picture taken of one game, newest first. */
- (NSString *)screenshotsJSONForSuite:(NSString *)suiteId;

/** One of those pictures, as PNG bytes. */
- (nullable NSData *)screenshotForSuite:(NSString *)suiteId named:(NSString *)name;

- (NSString *)deleteScreenshotForSuite:(NSString *)suiteId named:(NSString *)name;

#pragma mark - Saves

- (NSString *)savesJSONForSuite:(NSString *)suiteId;
- (NSString *)backupSuite:(NSString *)suiteId;
- (NSString *)restoreLatestForSuite:(NSString *)suiteId;
- (NSString *)resetDataForSuite:(NSString *)suiteId;

#pragma mark - Tools

- (NSString *)inspectJSONForSuite:(NSString *)suiteId;
- (nullable NSData *)resourceNamed:(NSString *)path inSuite:(NSString *)suiteId;

#pragma mark - Kho tài nguyên

/// Mọi thứ nằm trong tệp game: loại, định dạng, cỡ, và đã bị thay chưa.
- (NSString *)resourcesJSONForSuite:(NSString *)suiteId;
/// Thay một tệp trong game bằng tệp của người chơi; bản gốc không bị đụng tới.
- (NSString *)replaceResource:(NSString *)path
                    withData:(NSData *)data
                     inSuite:(NSString *)suiteId;
/// Trả một tệp về bản gốc trong game.
- (NSString *)restoreResource:(NSString *)path inSuite:(NSString *)suiteId;
/// Một tệp ảnh trong game, đổi sang PNG để màn hình vẽ được.
- (nullable NSData *)resourceImagePNG:(NSString *)path inSuite:(NSString *)suiteId;

#pragma mark - Emulator

- (NSString *)startGame:(NSString *)suiteId;
@property (nonatomic, readonly) BOOL isRunning;
@property (nonatomic, readonly) BOOL isFinished;
@property (nonatomic, readonly) NSString *activeSuiteId;
@property (nonatomic, readonly) CGSize screenSize;

/// Bảo game dừng ngay, kể cả khi nó đang kẹt giữa một khung hình.
- (NSString *)requestStopGame;

/// Advances one frame. Returns NO when nothing changed.
- (BOOL)renderFrame;

/// The current frame as an image, or nil when no game is running.
- (nullable CGImageRef)copyFrameImage CF_RETURNS_RETAINED;

- (nullable NSData *)screenshotPNG;

- (void)pressButton:(NSString *)button;

/// Labels the running screen has mapped to the two softkeys, as JSON.
- (NSString *)softKeysJSON;

/// Records the screen as an animation; saved beside the screenshots.
- (NSString *)startRecording;
- (NSString *)stopRecording;
- (NSString *)cancelRecording;
- (NSString *)recordingJSON;

/// How solid the running game's keypad should be drawn now, in percent.
- (NSInteger)keypadDrawOpacity;
/// Brings a faded keypad back, for a touch that is not a key press.
- (NSString *)noteKeypadUse;
/// The one game to offer on the way in, and starting it.
- (NSString *)continueJSON;
- (NSString *)continueGame;

/// Steering a game by tilting the phone.
- (NSString *)tiltJSONForSuite:(NSString *)suiteId;
- (NSString *)setTiltEnabled:(BOOL)enabled forSuite:(NSString *)suiteId;
- (NSString *)setTiltSensitivity:(NSInteger)percent forSuite:(NSString *)suiteId;
- (NSString *)setTiltAxes:(NSInteger)axes forSuite:(NSString *)suiteId;
- (NSString *)setTiltInverted:(BOOL)inverted forSuite:(NSString *)suiteId;
/// A lean, in thousandths, from -1000 to 1000 on each axis.
- (NSString *)tiltedX:(NSInteger)xMilli y:(NSInteger)yMilli;

#pragma mark - Máy ảo khai gì

/// Những gì game đọc được khi nó hỏi máy nó đang chạy trên đó là máy gì.
/// Chỉ để đọc: máy ảo là một cỗ máy duy nhất, bảng này là của chung.
- (NSString *)systemPropertiesJSON;

#pragma mark - Khi game hỏng

/// YES khi game vừa chết và lời giải thích chưa được đọc.
@property (nonatomic, readonly) BOOL hasCrashed;
/// Vì sao game chết, đã đọc thành lời: tiêu đề, lý do, việc phải làm.
- (NSString *)crashJSON;
/// Người chơi đã đọc xong; game đã chết cũng được dọn luôn.
- (NSString *)dismissCrash;

/// Gets one picture or clip ready to leave the app, under a readable name.
- (NSString *)shareScreenshot:(NSString *)name forSuite:(NSString *)suiteId;

/// Shelves the player puts their games on.
- (NSString *)collectionsJSONForSuite:(NSString *)suiteId;
- (NSString *)createCollection:(NSString *)name;
- (NSString *)toggleCollection:(NSString *)name forSuite:(NSString *)suiteId;
- (NSString *)renameCollection:(NSString *)from to:(NSString *)to;
- (NSString *)deleteCollection:(NSString *)name;
/// The games on one shelf, in the library's own listing shape.
- (NSString *)collectionJSON:(NSString *)name;

/// Installs a game from a link; these games arrive as a link first.
- (NSString *)installFromURL:(NSString *)url;
/// What the installer fetched, so the player can see where a game came from.
- (NSString *)downloadsJSON;

/// The files a game has written for itself through JSR-75.
- (NSString *)gameFilesJSONForSuite:(NSString *)suiteId;
- (NSString *)deleteGameFile:(NSString *)path forSuite:(NSString *)suiteId;

/// What a real controller's buttons do for one game.
- (NSString *)gamepadJSONForSuite:(NSString *)suiteId;
- (NSString *)setPadMapping:(NSString *)pad toButton:(NSString *)button
                   forSuite:(NSString *)suiteId;
- (NSString *)setGamepadEnabled:(BOOL)enabled forSuite:(NSString *)suiteId;
- (NSString *)resetGamepadForSuite:(NSString *)suiteId;
/// A control on a real pad was pressed or let go, by the emulator's own name.
- (NSString *)pressPad:(NSString *)pad;
- (NSString *)releasePad:(NSString *)pad;

/// Where the keys have been dragged to, and how big they are drawn.
- (NSString *)keypadArrangementJSONForSuite:(NSString *)suiteId;
/// Offsets are in thousandths of a key, not points: one arrangement has to
/// hold upright, sideways and on every size of screen.
- (NSString *)moveKey:(NSString *)button toX:(NSInteger)xMilli y:(NSInteger)yMilli
             forSuite:(NSString *)suiteId;
- (NSString *)setKeyScale:(NSInteger)percent forSuite:(NSString *)suiteId;
- (NSString *)resetKeypadForSuite:(NSString *)suiteId;

/// How the keypad looks for one game: solidity, key shape, fade delay.
- (NSString *)keypadJSONForSuite:(NSString *)suiteId;
- (NSString *)setKeypadOpacity:(NSInteger)percent forSuite:(NSString *)suiteId;
- (NSString *)setKeypadShape:(NSInteger)shape forSuite:(NSString *)suiteId;
- (NSString *)setKeypadFadeDelay:(NSInteger)seconds forSuite:(NSString *)suiteId;
- (void)releaseButton:(NSString *)button;
- (void)pointerDownAtX:(NSInteger)x y:(NSInteger)y;
- (void)pointerMovedToX:(NSInteger)x y:(NSInteger)y;
- (void)pointerUpAtX:(NSInteger)x y:(NSInteger)y;

- (void)pauseGame;
- (void)resumeGame;
- (void)stopGame;

- (NSString *)logText;
- (NSString *)logJSON;

@end

NS_ASSUME_NONNULL_END
