package dev.jax.skylyrics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Small in-game control panel for Spotify's official browser OAuth flow. */
final class SpotifyScreen extends Screen {
    private final Screen parent;
    private final Settings settings;
    private final Path tokenFile;
    private TextFieldWidget clientId;
    private String message = "";

    SpotifyScreen(Screen parent, Settings settings, Path tokenFile) {
        super(Text.literal("SkyLyrics — Spotify"));
        this.parent = parent;
        this.settings = settings;
        this.tokenFile = tokenFile;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        clientId = new TextFieldWidget(textRenderer, cx - 145, height / 2 - 52, 290, 20,
                Text.literal("Spotify Client ID"));
        clientId.setMaxLength(128);
        clientId.setPlaceholder(Text.literal("Paste your Spotify Developer Client ID"));
        clientId.setText(settings.spotifyClientId == null ? "" : settings.spotifyClientId);
        addDrawableChild(clientId);

        addDrawableChild(ButtonWidget.builder(Text.literal("Connect with Spotify"), b -> {
            settings.spotifyClientId = clientId.getText().trim();
            settings.save(FabricLoader.getInstance().getConfigDir().resolve("skylyrics/config.json"));
            message = "Opening browser...";
            SpotifyOAuth.authorize(settings.spotifyClientId, tokenFile);
        }).dimensions(cx - 145, height / 2 - 20, 140, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Disconnect"), b -> {
            SpotifyOAuth.disconnect(tokenFile);
            message = "Disconnected.";
        }).dimensions(cx + 5, height / 2 - 20, 140, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(cx - 50, height / 2 + 48, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 90, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Create an app at developer.spotify.com/dashboard"),
                width / 2, height / 2 - 72, 0xFFB8C0D0);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Add redirect URI: http://127.0.0.1:8888/callback"),
                width / 2, height / 2 + 12, 0xFFB8C0D0);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Status: " + SpotifyOAuth.status()),
                width / 2, height / 2 + 28, 0xFF8FE3B0);
        if (!message.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(message),
                width / 2, height / 2 + 40, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
