package kr.crownrpg.infra.core.realtime;

import io.netty.channel.Channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry mapping connected serverIds to Netty channels.
 */
public final class ChannelRegistry {

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();

    public Channel register(String serverId, Channel channel) {
        Channel previous = channels.put(serverId, channel);
        if (previous != null && previous != channel) {
            previous.close();
        }
        return previous;
    }

    public Channel find(String serverId) {
        return channels.get(serverId);
    }

    public void remove(Channel channel) {
        channels.entrySet().removeIf(entry -> entry.getValue() == channel);
    }

    public void closeAll() {
        channels.values().forEach(Channel::close);
        channels.clear();
    }
}
