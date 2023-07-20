package de.maxhenkel.wiretap.wiretap;

import com.mojang.authlib.GameProfile;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.wiretap.Wiretap;
import de.maxhenkel.wiretap.utils.HeadUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class WiretapManager {

    //TODO Regularly check for dead channels
    private final Map<UUID, DimensionLocation> microphones;
    private final Map<UUID, SpeakerChannel> speakers;

    public WiretapManager() {
        microphones = new HashMap<>();
        speakers = new HashMap<>();
    }

    public void onLoadHead(SkullBlockEntity skullBlockEntity) {
        if (!(skullBlockEntity.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        GameProfile ownerProfile = skullBlockEntity.getOwnerProfile();
        if (ownerProfile == null) {
            return;
        }

        UUID microphone = HeadUtils.getMicrophone(ownerProfile);
        if (microphone != null) {
            microphones.put(microphone, new DimensionLocation(serverLevel, skullBlockEntity.getBlockPos()));
            return;
        }

        UUID speaker = HeadUtils.getSpeaker(ownerProfile);
        if (speaker != null) {
            speakers.put(speaker, new SpeakerChannel(this, speaker, new DimensionLocation(serverLevel, skullBlockEntity.getBlockPos())));
            return;
        }
    }

    public List<UUID> getNearbyMicrophones(ServerLevel level, Vec3 pos) {
        double range = Wiretap.SERVER_CONFIG.microphonePickupRange.get();
        return microphones.entrySet().stream().filter(l -> l.getValue().isDimension(level)).filter(l -> l.getValue().getDistance(pos) <= range).map(Map.Entry::getKey).toList();
    }

    public void onMicPacket(MicrophonePacketEvent event) {
        VoicechatConnection senderConnection = event.getSenderConnection();
        if (senderConnection == null) {
            return;
        }
        ServerPlayer player = (ServerPlayer) senderConnection.getPlayer().getPlayer();
        ServerLevel serverLevel = player.serverLevel();

        onAudio(serverLevel, player.getUUID(), player.position(), event.getPacket().getOpusEncodedData());
    }

    private void onAudio(ServerLevel serverLevel, UUID sender, Vec3 senderLocation, byte[] opusEncodedData) {
        List<UUID> nearbyMicrophones = getNearbyMicrophones(serverLevel, senderLocation);

        for (UUID id : nearbyMicrophones) {
            verifyChannel(serverLevel, id);
            if (!microphones.containsKey(id)) {
                continue;
            }
            SpeakerChannel channel = speakers.get(id);
            if (channel == null) {
                continue;
            }
            channel.addPacket(sender, senderLocation, opusEncodedData);
        }
    }

    private long lastCheck = 0L;

    private void verifyChannel(ServerLevel serverLevel, UUID id) {
        long time = System.currentTimeMillis();
        if (time - lastCheck < 1000L) {
            return;
        }
        lastCheck = time;

        serverLevel.getServer().execute(() -> {
            DimensionLocation dimensionLocation = microphones.get(id);
            if (dimensionLocation == null) {
                return;
            }
            boolean valid = verifyMicrophoneLocation(id, dimensionLocation);
            if (!valid) {
                microphones.remove(id);
            }

            SpeakerChannel channel = speakers.get(id);
            if (channel == null) {
                return;
            }
            valid = verifySpeakerLocation(id, channel);
            if (!valid) {
                channel.close();
                speakers.remove(id);
            }
        });
    }

    public boolean verifyMicrophoneLocation(UUID microphoneId, @Nullable DimensionLocation location) {
        if (location == null) {
            return false;
        }
        ServerLevel level = location.getLevel();
        BlockPos pos = location.getPos();
        if (!location.isLoaded()) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SkullBlockEntity skullBlockEntity)) {
            return false;
        }
        GameProfile ownerProfile = skullBlockEntity.getOwnerProfile();
        if (ownerProfile == null) {
            return false;
        }
        UUID realMicrophoneId = HeadUtils.getMicrophone(ownerProfile);
        if (realMicrophoneId == null) {
            return false;
        }
        return realMicrophoneId.equals(microphoneId);
    }

    public boolean verifySpeakerLocation(UUID speakerId, @Nullable SpeakerChannel channel) {
        if (channel == null) {
            return false;
        }
        DimensionLocation dimensionLocation = channel.getDimensionLocation();
        ServerLevel level = dimensionLocation.getLevel();
        BlockPos pos = dimensionLocation.getPos();
        if (!dimensionLocation.isLoaded()) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof SkullBlockEntity skullBlockEntity)) {
            return false;
        }
        GameProfile ownerProfile = skullBlockEntity.getOwnerProfile();
        if (ownerProfile == null) {
            return false;
        }
        UUID realSpeakerId = HeadUtils.getSpeaker(ownerProfile);
        if (realSpeakerId == null) {
            return false;
        }
        return realSpeakerId.equals(speakerId);
    }

    public void removeMicrophone(UUID microphone) {
        microphones.remove(microphone);
    }

    public void removeSpeaker(UUID speaker) {
        SpeakerChannel speakerChannel = speakers.remove(speaker);
        if (speakerChannel != null) {
            speakerChannel.close();
        }
    }

    public void onPlayerDisconnect(ServerPlayer serverPlayer) {
        speakers.values().forEach(speakerChannel -> speakerChannel.onPlayerDisconnect(serverPlayer));
    }

    @Nullable
    public DimensionLocation getMicrophoneLocation(UUID microphone) {
        return microphones.get(microphone);
    }

    @Nullable
    public SpeakerChannel getSpeakerChannel(UUID speaker) {
        return speakers.get(speaker);
    }

    public void clear() {
        speakers.values().forEach(SpeakerChannel::close);
        speakers.clear();
        microphones.clear();
    }

    private static WiretapManager instance;

    public static WiretapManager getInstance() {
        if (instance == null) {
            instance = new WiretapManager();
        }
        return instance;
    }

}
