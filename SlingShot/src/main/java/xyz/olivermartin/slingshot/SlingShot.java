package xyz.olivermartin.slingshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class SlingShot extends Plugin implements Listener {

	private static SlingShot instance;
	public static File configDir;
	public static ConfigManager configman;

	public static final String LATEST_VERSION = "1.1";

	public static SlingShot getInstance() {
		return instance;
	}

	@EventHandler
	public void onLogin(PostLoginEvent event) {
		//
	}

	@EventHandler
	public void onServerSwitch(ServerSwitchEvent event) {
		//
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onServerKick(ServerKickEvent event) {

		ProxiedPlayer p = event.getPlayer();
		ServerInfo s = event.getKickedFrom();

		// If the player is kicked from a server that should not use slingshot
		if (configman.config.getStringList("no_slingshot").contains(s.getName())) {
			return;
		}

		// If the player is kicked from the "slingshot" server
		if (s.getName().equals(configman.config.getString("target"))) {

			p.disconnect(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', configman.config.getString("kick-message"))));
			event.setCancelled(true);
			return;

		}

		event.setCancelServer(ProxyServer.getInstance().getServerInfo(configman.config.getString("target")));
		event.setCancelled(true);

		p.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', configman.config.getString("message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())))));

	}

	public void onEnable() {

		instance = this;
		configDir = getDataFolder();
		configman = new ConfigManager();

		if (!getDataFolder().exists()) {

			System.out.println("[SlingShot] Creating plugin directory!");
			getDataFolder().mkdirs();

		}

		configman.startupConfig();

		if (!configman.config.getString("version").equals(LATEST_VERSION)) {

			System.out.println("[SlingShot] CONFIG VERSION INCORRECT - Your config.yml is outdated. It will be saved as config-old.yml, and a fresh config.yml will be created.");

			File f = new File(SlingShot.configDir + "\\config.yml");

			try {
				Files.copy(f.toPath(), new File(SlingShot.configDir, "config-old.yml").toPath(), new CopyOption[0]);
				Files.deleteIfExists(f.toPath());
				configman.startupConfig();
			} catch (IOException e) {
				System.out.println("[SlingShot] [ERROR] Could not move old config. Plugin will not work until this config is deleted.");
				e.printStackTrace();
				return;
			}

		}

		getProxy().getPluginManager().registerListener(this, this);
		getProxy().getPluginManager().registerCommand(this, new SlingShotCommand());

	}

	public void onDisable() {
		getLogger().info("Thankyou for using SlingShot. Disabling...");
	}

}
