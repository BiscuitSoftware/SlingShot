package xyz.olivermartin.slingshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;

import org.bstats.bungeecord.Metrics;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
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
	public static boolean debug = false;

	public static final String LATEST_VERSION = "1.2";

	public static void debugMessage(String message) {
		if (debug) ProxyServer.getInstance().getLogger().info("[DEBUG] " + message);
	}

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

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.LOWEST)
	public void onServerKick(ServerKickEvent event) {

		debugMessage("--- START KICK EVENT ---");

		ProxiedPlayer p = event.getPlayer();
		ServerInfo s = event.getKickedFrom();

		debugMessage("Player: " + p.getName());
		debugMessage("Kicked From: " + s.getName());
		debugMessage("Kick Reason: " + event.getKickReason());

		if (event.isCancelled()) {
			debugMessage("EVENT IS ALREADY CANCELLED");
			debugMessage("--- END KICK EVENT ---");
			return;
		}



		// This is a fix for an issue in Waterfall that calls this server kick event when players disconnect from a non-lobby server
		if (event.getKickReason().contains("[Proxy] Lost connection to server")) {
			debugMessage("This kick is just a waterfall server switch... Ignore it...");
			debugMessage("--- END KICK EVENT ---");
			return;
		}

		// If the player is kicked from a server that should not use slingshot
		if (configman.config.getStringList("no_slingshot").contains(s.getName())) {
			debugMessage("Player is kicked from a server that doesnt use slingshot");
			debugMessage("--- END KICK EVENT ---");
			return;
		}

		/* END THE EXIT CONDITIONS */

		// If the player is kicked from the "slingshot" server
		if (s.getName().equalsIgnoreCase(configman.config.getString("target"))) {

			debugMessage("Player is being kicked from the slingshot server");
			debugMessage("Disconnect with reason: " + configman.config.getString("kick-message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())));

			p.disconnect(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', configman.config.getString("kick-message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())))));
			event.setCancelled(true);

			debugMessage("--- END KICK EVENT ---");
			return;

		}

		debugMessage("This is a regular kick that needs dealing with by slingshot.");



		// Check if the target server is online
		getProxy().getServers().get(configman.config.getString("target")).ping(new Callback<ServerPing>() {

			@Override
			public void done(ServerPing result, Throwable error) {

				boolean targetOnline = (error == null);

				synchronized (event) {

					// TODO: The issue is, ONLY SOMETIMES (need to issue a few kicks to see it), the player being kicked from a server while the lobby is down will result in the correct message not being shown. Instead they see the red text error message about a fallback server not being available. im guessing that happens when this thread takes too long to respond.

					// If the target server is online then connect to it, otherwise kick the player
					if (targetOnline) {

						debugMessage("The target server is online.");

						//TODO try removing this all together?event.getPlayer().connect(ProxyServer.getInstance().getServerInfo(configman.config.getString("target")));

						event.setCancelServer(ProxyServer.getInstance().getServerInfo(configman.config.getString("target")));

						debugMessage("Player will be connected to the target server on kick.");

						p.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', configman.config.getString("message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())))));

						debugMessage("Player notified with message: " + configman.config.getString("message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())));
						debugMessage("--- END KICK EVENT ---");

					} else {

						debugMessage("The target server is not online.");
						debugMessage("Disconnect player with reason: " + configman.config.getString("kick-message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())));

						p.disconnect(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', configman.config.getString("kick-message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())))));

						debugMessage("--- END KICK EVENT ---");

					}

					event.notify();

				}

				return;

			}

		});

		synchronized (event) {

			try {
				event.wait(250);
			} catch (InterruptedException e) {
				System.err.println("SlingShot didn't get a response from your servers in time. Your server must be lagging!");
				/*EMPTY*/
			}

			event.setCancelled(true);

		}

	}

	public void onEnable() {

		instance = this;
		configDir = getDataFolder();
		configman = new ConfigManager();

		@SuppressWarnings("unused")
		Metrics metrics = new Metrics(this);

		if (!getDataFolder().exists()) {

			System.out.println("[SlingShot] Creating plugin directory!");
			getDataFolder().mkdirs();

		}

		configman.startupConfig();

		if (! (configman.config.getString("version").equals(LATEST_VERSION) )) {

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
