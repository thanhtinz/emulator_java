#import "MobiCoreAudioSink.h"

#import <AVFoundation/AVFoundation.h>

#import "core/com/mobicore/core/audio/AudioClip.h"
#import "core/com/mobicore/core/audio/WavDecoder.h"

/// More voices than a J2ME game ever plays at once, and a cap on leaks.
static const NSInteger kMaxVoices = 8;

@implementation MobiCoreAudioSink {
    NSMutableDictionary<NSNumber *, AVAudioPlayer *> *_voices;
    NSInteger _nextVoice;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _voices = [NSMutableDictionary dictionary];
        _nextVoice = 0;
        // Ambient: a game's sound effects should not silence music the user
        // already had playing, and should follow the ringer switch.
        NSError *error = nil;
        [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryAmbient error:&error];
        [[AVAudioSession sharedInstance] setActive:YES error:&error];
    }
    return self;
}

- (jint)startWithComMobicoreCoreAudioAudioClip:(ComMobicoreCoreAudioAudioClip *)clip
                                      withInt:(jint)loops
                                      withInt:(jint)volume {
    [self reclaim];
    if (_voices.count >= kMaxVoices || clip == nil) {
        return ComMobicoreCoreAudioAudioSink_NO_VOICE;
    }

    // The core can already write a WAV header around its own samples, which
    // is the one format AVAudioPlayer takes as bytes. Re-encoding here would
    // be the same code written twice.
    IOSByteArray *wav = ComMobicoreCoreAudioWavDecoder_encodeWithComMobicoreCoreAudioAudioClip_(clip);
    if (wav == nil || wav->size_ == 0) {
        return ComMobicoreCoreAudioAudioSink_NO_VOICE;
    }
    NSData *data = [NSData dataWithBytes:wav->buffer_ length:(NSUInteger) wav->size_];

    NSError *error = nil;
    AVAudioPlayer *player = [[AVAudioPlayer alloc] initWithData:data error:&error];
    if (player == nil) {
        return ComMobicoreCoreAudioAudioSink_NO_VOICE;
    }
    // MIDP counts plays; AVAudioPlayer counts repeats, and a negative number
    // is forever.
    player.numberOfLoops = loops <= 0 ? -1 : loops - 1;
    player.volume = MAX(0, MIN(100, volume)) / 100.0f;
    [player prepareToPlay];
    if (![player play]) {
        return ComMobicoreCoreAudioAudioSink_NO_VOICE;
    }

    NSInteger voice = _nextVoice++;
    _voices[@(voice)] = player;
    return (jint) voice;
}

- (void)stopWithInt:(jint)voice {
    AVAudioPlayer *player = _voices[@(voice)];
    if (player != nil) {
        [player stop];
        [_voices removeObjectForKey:@(voice)];
    }
}

- (void)setVolumeWithInt:(jint)voice withInt:(jint)volume {
    _voices[@(voice)].volume = MAX(0, MIN(100, volume)) / 100.0f;
}

- (jboolean)isPlayingWithInt:(jint)voice {
    return _voices[@(voice)].playing ? true : false;
}

- (jlong)positionMsWithInt:(jint)voice {
    AVAudioPlayer *player = _voices[@(voice)];
    return player == nil ? 0 : (jlong) (player.currentTime * 1000.0);
}

- (void)releaseAll {
    for (AVAudioPlayer *player in _voices.allValues) {
        [player stop];
    }
    [_voices removeAllObjects];
}

/// Drops players that have finished, so a long session does not pile up.
- (void)reclaim {
    NSArray<NSNumber *> *keys = _voices.allKeys;
    for (NSNumber *key in keys) {
        if (!_voices[key].playing) {
            [_voices removeObjectForKey:key];
        }
    }
}

@end
