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
- (NSString *)setDeviceProfile:(NSString *)deviceId forSuite:(NSString *)suiteId;
- (NSString *)setInputPreset:(NSString *)preset forSuite:(NSString *)suiteId;
- (NSString *)toggleFavouriteForSuite:(NSString *)suiteId;

/** Portrait to landscape and back, remembered with the game. */
- (NSString *)toggleOrientationForSuite:(NSString *)suiteId;

/** Full keypad, arrows only, numbers only, hidden — and round again. */
- (NSString *)cycleKeypadLayoutForSuite:(NSString *)suiteId;

/** Saves a picture of what the running game is showing. */
- (NSString *)takeScreenshot;

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

#pragma mark - Emulator

- (NSString *)startGame:(NSString *)suiteId;
@property (nonatomic, readonly) BOOL isRunning;
@property (nonatomic, readonly) BOOL isFinished;
@property (nonatomic, readonly) NSString *activeSuiteId;
@property (nonatomic, readonly) CGSize screenSize;

/// Advances one frame. Returns NO when nothing changed.
- (BOOL)renderFrame;

/// The current frame as an image, or nil when no game is running.
- (nullable CGImageRef)copyFrameImage CF_RETURNS_RETAINED;

- (nullable NSData *)screenshotPNG;

- (void)pressButton:(NSString *)button;

/// Labels the running screen has mapped to the two softkeys, as JSON.
- (NSString *)softKeysJSON;
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
