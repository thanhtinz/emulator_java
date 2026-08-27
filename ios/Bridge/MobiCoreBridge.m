#import "MobiCoreBridge.h"

#import "J2ObjC_header.h"
#import "IOSPrimitiveArray.h"
#import "MobiCoreAudioSink.h"
#import "MobiCoreVibration.h"
#import "com/mobicore/core/bridge/MobiCoreFacade.h"
#import "com/mobicore/core/midp/SystemChrome.h"

@implementation MobiCoreBridge {
    ComMobicoreCoreBridgeMobiCoreFacade *_facade;
    /// Reused across frames: a 240x320 screen is 300 KB and reallocating it
    /// sixty times a second would churn memory for no reason.
    uint32_t *_pixelBuffer;
    NSUInteger _pixelCapacity;
    MobiCoreAudioSink *_audio;
    MobiCoreVibration *_vibration;
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
        _vibration = [[MobiCoreVibration alloc] init];
        [_facade setVibrationSinkWithComMobicoreCoreHapticsVibrationSink:_vibration];
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

- (NSString *)importMany:(NSArray<NSString *> *)names payloads:(NSArray<NSData *> *)payloads {
    IOSObjectArray *nameArray = [IOSObjectArray arrayWithLength:names.count
                                                           type:NSString_class_()];
    for (NSUInteger i = 0; i < names.count; i++) {
        [nameArray replaceObjectAtIndex:i withObject:names[i]];
    }
    IOSObjectArray *payloadArray =
            [IOSObjectArray arrayWithLength:payloads.count
                                       type:IOSClass_arrayType([IOSClass byteClass], 1)];
    for (NSUInteger i = 0; i < payloads.count; i++) {
        [payloadArray replaceObjectAtIndex:i withObject:[self byteArrayFrom:payloads[i]]];
    }
    return [_facade importManyWithNSStringArray:nameArray withByteArray2:payloadArray];
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

- (NSString *)setInputPreset:(NSString *)preset forSuite:(NSString *)suiteId {
    return [_facade setInputPresetWithNSString:suiteId withNSString:preset];
}

- (NSString *)toggleOrientationForSuite:(NSString *)suiteId {
    return [_facade toggleOrientationWithNSString:suiteId];
}

- (NSString *)cycleKeypadLayoutForSuite:(NSString *)suiteId {
    return [_facade cycleKeypadLayoutWithNSString:suiteId];
}

- (NSString *)takeScreenshot {
    return [_facade takeScreenshot];
}

- (NSString *)midletsJSONForSuite:(NSString *)suiteId {
    return [_facade midletsJsonWithNSString:suiteId];
}

- (NSString *)startGame:(NSString *)suiteId midlet:(NSString *)midletClass {
    return [_facade startGameWithNSString:suiteId withNSString:midletClass];
}

- (NSData *)exportLibrary {
    IOSByteArray *bytes = [_facade exportLibrary];
    if (bytes == nil || bytes->size_ == 0) {
        return nil;
    }
    return [NSData dataWithBytes:bytes->buffer_ length:(NSUInteger)bytes->size_];
}

- (NSString *)importLibrary:(NSData *)archive {
    IOSByteArray *bytes = [IOSByteArray arrayWithBytes:(const jbyte *)archive.bytes
                                                 count:(jint)archive.length];
    return [_facade importLibraryWithByteArray:bytes];
}

- (NSString *)setKeyMapping:(int32_t)keyCode forButton:(NSString *)button suite:(NSString *)suiteId {
    return [_facade setKeyMappingWithNSString:suiteId withNSString:button withInt:keyCode];
}

- (NSString *)keyChoicesJSON {
    return [_facade keyChoicesJson];
}

- (NSString *)setTurbo:(int32_t)intervalMs forButton:(NSString *)button suite:(NSString *)suiteId {
    return [_facade setTurboWithNSString:suiteId withNSString:button withInt:intervalMs];
}

- (NSString *)rewindStep {
    return [_facade rewindStep];
}

- (NSString *)rewindJSON {
    return [_facade rewindJson];
}

- (NSString *)setRewindEnabled:(BOOL)enabled {
    return [_facade setRewindEnabledWithBoolean:enabled];
}

- (NSString *)cycleSpeed {
    return [_facade cycleSpeed];
}

- (NSString *)speedJSON {
    return [_facade speedJson];
}

- (NSString *)saveStateInSlot:(int32_t)slot {
    return [_facade saveStateWithInt:slot];
}

- (NSString *)loadStateFromSlot:(int32_t)slot {
    return [_facade loadStateWithInt:slot];
}

- (NSString *)saveStatesJSONForSuite:(NSString *)suiteId {
    return [_facade saveStatesJsonWithNSString:suiteId];
}

- (NSData *)saveStateThumbnailForSuite:(NSString *)suiteId slot:(int32_t)slot {
    IOSByteArray *bytes = [_facade saveStateThumbnailWithNSString:suiteId withInt:slot];
    if (bytes == nil || bytes->size_ == 0) {
        return nil;
    }
    return [NSData dataWithBytes:bytes->buffer_ length:(NSUInteger)bytes->size_];
}

- (NSString *)deleteSaveStateForSuite:(NSString *)suiteId slot:(int32_t)slot {
    return [_facade deleteSaveStateWithNSString:suiteId withInt:slot];
}

- (NSString *)presetsJSON {
    return [_facade presetsJson];
}

- (NSString *)savePreset:(NSString *)name fromSuite:(NSString *)suiteId {
    return [_facade savePresetWithNSString:name withNSString:suiteId];
}

- (NSString *)applyPreset:(NSString *)name toSuite:(NSString *)suiteId {
    return [_facade applyPresetWithNSString:name withNSString:suiteId];
}

- (NSString *)deletePreset:(NSString *)name {
    return [_facade deletePresetWithNSString:name];
}

- (NSString *)setDefaultPreset:(NSString *)name {
    return [_facade setDefaultPresetWithNSString:name];
}

- (NSString *)screenshotsJSONForSuite:(NSString *)suiteId {
    return [_facade screenshotsJsonWithNSString:suiteId];
}

- (NSData *)screenshotForSuite:(NSString *)suiteId named:(NSString *)name {
    IOSByteArray *bytes = [_facade screenshotWithNSString:suiteId withNSString:name];
    if (bytes == nil || bytes->size_ == 0) {
        return nil;
    }
    return [NSData dataWithBytes:bytes->buffer_ length:(NSUInteger)bytes->size_];
}

- (NSString *)deleteScreenshotForSuite:(NSString *)suiteId named:(NSString *)name {
    return [_facade deleteScreenshotWithNSString:suiteId withNSString:name];
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

- (NSString *)startRecording {
    return [_facade startRecording];
}

- (NSString *)stopRecording {
    return [_facade stopRecording];
}

- (NSString *)cancelRecording {
    return [_facade cancelRecording];
}

- (NSString *)recordingJSON {
    return [_facade recordingJson];
}

- (NSInteger)keypadDrawOpacity {
    return [_facade keypadDrawOpacity];
}

- (NSString *)noteKeypadUse {
    return [_facade noteKeypadUse];
}

- (NSString *)keypadArrangementJSONForSuite:(NSString *)suiteId {
    return [_facade keypadArrangementJsonWithNSString:suiteId];
}

- (NSString *)moveKey:(NSString *)button toX:(NSInteger)xMilli y:(NSInteger)yMilli
             forSuite:(NSString *)suiteId {
    return [_facade moveKeyWithNSString:suiteId
                           withNSString:button
                                withInt:(jint) xMilli
                                withInt:(jint) yMilli];
}

- (NSString *)setKeyScale:(NSInteger)percent forSuite:(NSString *)suiteId {
    return [_facade setKeyScaleWithNSString:suiteId withInt:(jint) percent];
}

- (NSString *)resetKeypadForSuite:(NSString *)suiteId {
    return [_facade resetKeypadWithNSString:suiteId];
}

- (NSString *)keypadJSONForSuite:(NSString *)suiteId {
    return [_facade keypadJsonWithNSString:suiteId];
}

- (NSString *)setKeypadOpacity:(NSInteger)percent forSuite:(NSString *)suiteId {
    return [_facade setKeypadOpacityWithNSString:suiteId withInt:(jint) percent];
}

- (NSString *)setKeypadShape:(NSInteger)shape forSuite:(NSString *)suiteId {
    return [_facade setKeypadShapeWithNSString:suiteId withInt:(jint) shape];
}

- (NSString *)setKeypadFadeDelay:(NSInteger)seconds forSuite:(NSString *)suiteId {
    return [_facade setKeypadFadeDelayWithNSString:suiteId withInt:(jint) seconds];
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
