package uk.co.caprica.vlcj.player.list;

import com.sun.jna.CallbackThreadInitializer;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import uk.co.caprica.vlcj.binding.internal.libvlc_callback_t;
import uk.co.caprica.vlcj.binding.internal.libvlc_event_e;
import uk.co.caprica.vlcj.binding.internal.libvlc_event_manager_t;
import uk.co.caprica.vlcj.binding.internal.libvlc_event_t;
import uk.co.caprica.vlcj.player.list.events.MediaListPlayerEvent;
import uk.co.caprica.vlcj.player.list.events.MediaListPlayerEventFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventService extends BaseService {

    /**
     * Native media player event manager.
     */
    private final libvlc_event_manager_t mediaListPlayerEventManager;

    /**
     * Collection of media player event listeners.
     * <p>
     * A {@link CopyOnWriteArrayList} is used defensively so as not to interfere with the processing of any existing
     * events that may be being generated by the native callback in the unlikely case that a listeners is being added or
     * removed.
     */
    private final List<MediaListPlayerEventListener> eventListenerList = new CopyOnWriteArrayList<MediaListPlayerEventListener>();

    /**
     * Call-back to handle native media list player events.
     */
    private final MediaListPlayerEventCallback callback = new MediaListPlayerEventCallback();

    EventService(DefaultMediaListPlayer mediaListPlayer) {
        super(mediaListPlayer);

        this.mediaListPlayerEventManager = getNativeMediaListPlayerEventManager();

        registerNativeEventListener();
    }

    private libvlc_event_manager_t getNativeMediaListPlayerEventManager() {
        libvlc_event_manager_t result = libvlc.libvlc_media_list_player_event_manager(mediaListPlayerInstance);
        if (result != null) {
            return result;
        } else {
            throw new RuntimeException("Failed to get the native media player event manager instance");
        }
    }

    /**
     * Add a component to be notified of media player events.
     *
     * @param listener component to notify
     */
    public void addMediaListPlayerEventListener(MediaListPlayerEventListener listener) {
        eventListenerList.add(listener);
    }

    /**
     * Remove a component that was previously interested in notifications of media player events.
     *
     * @param listener component to stop notifying
     */
    public void removeMediaListPlayerEventListener(MediaListPlayerEventListener listener) {
        eventListenerList.remove(listener);
    }

    private void registerNativeEventListener() {
        for (libvlc_event_e event : libvlc_event_e.values()) {
            if (event.intValue() >= libvlc_event_e.libvlc_MediaListPlayerPlayed.intValue() && event.intValue() <= libvlc_event_e.libvlc_MediaListPlayerStopped.intValue()) {
                libvlc.libvlc_event_attach(mediaListPlayerEventManager, event.intValue(), callback, null);
            }
        }
    }

    private void deregisterNativeEventListener() {
        for (libvlc_event_e event : libvlc_event_e.values()) {
            if (event.intValue() >= libvlc_event_e.libvlc_MediaListPlayerPlayed.intValue() && event.intValue() <= libvlc_event_e.libvlc_MediaListPlayerStopped.intValue()) {
                libvlc.libvlc_event_detach(mediaListPlayerEventManager, event.intValue(), callback, null);
            }
        }
    }

    /**
     * Raise a new event (dispatch it to listeners).
     * <p>
     * Events are processed on the <em>native</em> callback thread, so must execute quickly and certainly must never
     * block.
     * <p>
     * It is also generally <em>forbidden</em> for an event handler to call back into LibVLC.
     *
     * @param mediaListPlayerEvent event to raise, may be <code>null</code> and if so will be ignored
     */
    void raiseEvent(MediaListPlayerEvent mediaListPlayerEvent) {
        if (mediaListPlayerEvent != null) {
            for (MediaListPlayerEventListener listener : eventListenerList) {
                mediaListPlayerEvent.notify(listener);
            }
        }
    }

    private class MediaListPlayerEventCallback implements libvlc_callback_t {

        private MediaListPlayerEventCallback() {
            Native.setCallbackThreadInitializer(this, new CallbackThreadInitializer(true, false, "media-list-player-events"));
        }

        @Override
        public void callback(libvlc_event_t event, Pointer userData) {
            raiseEvent(MediaListPlayerEventFactory.createEvent(mediaListPlayer, event));
        }
    }

    @Override
    protected void release() {
        eventListenerList.clear();

        deregisterNativeEventListener();
    }

}