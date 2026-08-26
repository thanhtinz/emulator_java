#import "MobiCoreBridge.h"

#import "J2ObjC_header.h"
#import "IOSPrimitiveArray.h"
#import "MobiCoreAudioSink.h"
#import "com/mobicore/core/bridge/MobiCoreFacade.h"
#import "com/mobicore/core/midp/SystemChrome.h"

@implementation MobiCoreBridge {
    ComMobicoreCoreBridgeMobiCoreFacade *_facade;
    /// Reused across frames: a 240x320 screen is 300 KB and reallocating it
    /// sixty times a second would churn memory for no reason.
    uint32_t *_pixelBuffer;
    NSUInteger _pixelCapacity;
    MobiCoreAudioSink *_audio;
}

+ (MobiCoreBridge *)shared {
    static MobiCoreBridge *instance;
    static dispatch_once_t token;
    dispatch_once(&token, ^{
        instance = [[MobiCoreBridge alloc] init];
    });
    return instance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _facade = [[ComMobicoreCoreBridgeMobiCoreFacade alloc] init];
        // Handed over once: without it the emulator still plays every sound,
        // into a recorder nobody can hear.
        _audio = [[MobiCoreAudioSink alloc] init];
        [_facade setAudioSinkWithComMobicoreCoreAudioAudioSink:_audio];
    }
    return self;
}

- (void)dealloc {
    free(_pixelBuffer);
}

#pragma mark - Library

- (NSString *)openAtPath:(NSString *)root {
    return [_facade openWithNSString:root];
}

- (BOOL)isOpen {
    return [_facade isOpen];
}

- (NSString *)storageRoot {
    return [_facade storageRoot];
}

- (NSString *)libraryJSON {
    return [_facade libraryJson];
}

- (NSData *)artworkForSuite:(NSString *)suiteId {
    return [self dataFrom:[_facade artworkWithNSString:suiteId]];
}

- (NSString *)importJar:(NSData *)jar descriptor:(NSData *)descriptor {
    return [_facade importSuiteWithByteArray:[self byteArrayFrom:jar]
                               withByteArray:[self byteArrayFrom:descriptor]];
}

- (NSString *)uninstallSuite:(NSString *)suiteId keepData:(BOOL)keepData {
    return [_facade uninstallWithNSString:suiteId withBoolean:keepData];
}

- (NSString *)renameSuite:(NSString *)suiteId title:(NSString *)title {
    return [_facade renameGameWithNSString:suiteId withNSString:title];
}

- (NSString *)resetTitleForSuite:(NSString *)suiteId {
    return [_facade resetTitleWithNSString:suiteId];
}

- (NSString *)setArtwork:(NSData *)png forSuite:(NSString *)suiteId {
    return [_facade setArtworkWithNSString:suiteId withByteArray:[self byteArrayFrom:png]];
}

- (NSString *)resetArtworkForSuite:(NSString *)suiteId {
    return [_facade resetArtworkWithNSString:suiteId];
}

- (NSString *)autoSetupForSuite:(NSString *)suiteId {
    return [_facade autoSetupWithNSString:suiteId];
}

- (NSString *)searchJSON:(NSString *)query sort:(int32_t)sort {
    return [_facade searchJsonWithNSString:query withInt:sort];
}

- (NSString *)setLibrarySort:(int32_t)sort {
    return [_facade setLibrarySortWithInt:sort];
}

#pragma mark - Appearance

- (NSString *)appSettingsJSON {
    return [_facade appSettingsJson];
}

- (NSString *)setTheme:(int32_t)theme {
    return [_facade setThemeWithInt:theme];
}

- (NSString *)cycleTheme {
    return [_facade cycleTheme];
}

- (void)setChromeDark:(BOOL)dark {
    ComMobicoreCoreMidpSystemChrome_setDarkWithBoolean_(dark);
}

#pragma mark - Text entry

- (BOOL)isTextInputActive {
    return [_facade isTextInputActive];
}

- (NSString *)textInput {
    return [_facade textInput];
}

- (NSString *)setTextInput:(NSString *)value {
    return [_facade setTextInputWithNSString:value];
}

#pragma mark - Save states

- (NSString *)saveState {
    return [_facade saveState];
}

- (NSString *)startGameResuming:(NSString *)suiteId {
    return [_facade resumeGameWithNSString:suiteId];
}

- (BOOL)hasSaveStateForSuite:(NSString *)suiteId {
    return [_facade hasSaveStateWithNSString:suiteId];
}

- (NSData *)saveStateThumbnailForSuite:(NSString *)suiteId {
    return [self dataFrom:[_facade saveStateThumbnailWithNSString:suiteId]];
}

- (NSString *)deleteSaveStateForSuite:(NSString *)suiteId {
    return [_facade deleteSaveStateWithNSString:suiteId];
}

- (NSString *)stopGameSaving {
    [_audio releaseAll];
    return [_facade stopGameSaving];
}

#pragma mark - Profiles

- (NSString *)profileJSONForSuite:(NSString *)suiteId {
    return [_facade profileJsonWithNSString:suiteId];
}

- (NSString *)updateProfileJSON:(NSString *)json {
    return [_facade updateProfileWithNSString:json];
}

- (NSString *)setDeviceProfile:(NSString *)deviceId forSuite:(NSString *)suiteId {
    return [_facade setDeviceProfileWithNSString:suiteId withNSString:deviceId];
}

- (NSString *)setInputPreset:(NSString *)preset forSuite:(NSString *)suiteId {
    return [_facade setInputPresetWithNSString:suiteId withNSString:preset];
}

- (NSString *)toggleFavouriteForSuite:(NSString *)suiteId {
    return [_facade toggleFavouriteWithNSString:suiteId];
}

#pragma mark - Saves

- (NSString *)savesJSONForSuite:(NSString *)suiteId {
    return [_facade savesJsonWithNSString:suiteId];
}

- (NSString *)backupSuite:(NSString *)suiteId {
    return [_facade backupWithNSString:suiteId];
}

- (NSString *)restoreLatestForSuite:(NSString *)suiteId {
    return [_facade restoreLatestWithNSString:suiteId];
}

- (NSString *)resetDataForSuite:(NSString *)suiteId {
    return [_facade resetGameDataWithNSString:suiteId];
}

#pragma mark - Tools

- (NSString *)inspectJSONForSuite:(NSString *)suiteId {
    return [_facade inspectJsonWithNSString:suiteId];
}

- (NSData *)resourceNamed:(NSString *)path inSuite:(NSString *)suiteId {
    return [self dataFrom:[_facade resourceWithNSString:suiteId withNSString:path]];
}

#pragma mark - Emulator

- (NSString *)startGame:(NSString *)suiteId {
    return [_facade startGameWithNSString:suiteId];
}

- (BOOL)isRunning {
    return [_facade isRunning];
}

- (BOOL)isFinished {
    return [_facade isFinished];
}

- (NSString *)activeSuiteId {
    return [_facade activeSuiteId];
}

- (CGSize)screenSize {
    return CGSizeMake([_facade screenWidth], [_facade screenHeight]);
}

- (BOOL)renderFrame {
    return [_facade renderFrame];
}

- (CGImageRef)copyFrameImage {
    jint width = [_facade screenWidth];
    jint height = [_facade screenHeight];
    if (width <= 0 || height <= 0) {
        return NULL;
    }
    IOSIntArray *pixels = [_facade framePixels];
    NSUInteger count = (NSUInteger)(width * height);
    if (pixels == nil || (NSUInteger)pixels->size_ < count) {
        return NULL;
    }
    if (_pixelCapacity < count) {
        free(_pixelBuffer);
        _pixelBuffer = malloc(count * sizeof(uint32_t));
        _pixelCapacity = _pixelBuffer ? count : 0;
    }
    if (_pixelBuffer == NULL) {
        return NULL;
    }
    // The emulator stores 0xAARRGGBB; Core Graphics reads it as alpha-first
    // 32-bit big endian, so the words copy across untouched.
    memcpy(_pixelBuffer, pixels->buffer_, count * sizeof(uint32_t));

    CGColorSpaceRef space = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = CGBitmapContextCreate(
        _pixelBuffer, (size_t)width, (size_t)height, 8, (size_t)width * 4, space,
        kCGImageAlphaNoneSkipFirst | kCGBitmapByteOrder32Big);
    CGColorSpaceRelease(space);
    if (context == NULL) {
        return NULL;
    }
    CGImageRef image = CGBitmapContextCreateImage(context);
    CGContextRelease(context);
    return image;
}

- (NSData *)screenshotPNG {
    return [self dataFrom:[_facade screenshotPng]];
}

- (void)pressButton:(NSString *)button {
    [_facade pressButtonWithNSString:button];
}

- (NSString *)softKeysJSON {
    return [_facade softKeysJson];
}

- (void)releaseButton:(NSString *)button {
    [_facade releaseButtonWithNSString:button];
}

- (void)pointerDownAtX:(NSInteger)x y:(NSInteger)y {
    [_facade pointerPressedWithInt:(jint)x withInt:(jint)y];
}

- (void)pointerMovedToX:(NSInteger)x y:(NSInteger)y {
    [_facade pointerDraggedWithInt:(jint)x withInt:(jint)y];
}

- (void)pointerUpAtX:(NSInteger)x y:(NSInteger)y {
    [_facade pointerReleasedWithInt:(jint)x withInt:(jint)y];
}

- (void)pauseGame {
    [_facade pauseGame];
}

- (void)resumeGame {
    [_facade resumeGame];
}

- (void)stopGame {
    [_audio releaseAll];
    [_facade stopGame];
}

- (NSString *)logText {
    return [_facade logText];
}

- (NSString *)logJSON {
    return [_facade logJson];
}

#pragma mark - Conversions

/// Empty arrays mean "nothing there", which reads better as nil in Swift.
- (NSData *)dataFrom:(IOSByteArray *)array {
    if (array == nil || array->size_ == 0) {
        return nil;
    }
    return [NSData dataWithBytes:array->buffer_ length:(NSUInteger)array->size_];
}

- (IOSByteArray *)byteArrayFrom:(NSData *)data {
    if (data == nil) {
        return [IOSByteArray arrayWithLength:0];
    }
    return [IOSByteArray arrayWithBytes:(const jbyte *)data.bytes count:(jint)data.length];
}

@end
