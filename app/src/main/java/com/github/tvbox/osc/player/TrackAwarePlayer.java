package com.github.tvbox.osc.player;

/**
 * Abstraction over players that expose audio-track selection keyed by track id
 * (e.g. {@link IjkmPlayer}). Lets karaoke code stay player-agnostic so ExoPlayer
 * / Media3 can be wired in later without touching the call sites.
 */
public interface TrackAwarePlayer {
    TrackInfo getTrackInfo();
    void setTrack(int trackId);
}
