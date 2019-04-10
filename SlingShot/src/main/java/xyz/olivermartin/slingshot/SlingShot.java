package xyz.olivermartin.slingshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.bstats.bungeecord.Metrics;

import net.md_5.bungee.api.Callback;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerKickEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class SlingShot extends Plugin implements Listener {

	/* START STATIC */

	private static SlingShot instance;
	public static File configDir;
	public static ConfigManager configman;

	public static boolean debug;
	public static final String LATEST_VERSION = "2.0";
	public static final String[] ALLOWED_VERSIONS = {
			LATEST_VERSION
	};
	private static Map<String, Boolean> onlineMap;

	/* START STATIC METHODS */

	public static void debugMessage(String message) {
		if (debug) ProxyServer.getInstance().getLogger().info("[DEBUG] " + message);
	}

	public static SlingShot getInstance() {
		return instance;
	}

	public static List<String> getTargetList() {
		return configman.config.getStringList("target-list");
	}

	public static boolean isReasonExempt(String reason) {

		boolean whitelist = configman.config.getBoolean("use_as_whitelist");

		boolean matchesList = configman.config.getStringList("kick_reason_blacklist").stream().anyMatch(x -> reason.matches(x));

		return whitelist ^ matchesList;

	}

	/* END STATIC */

	public void onEnable() {

		instance = this;
		configDir = getDataFolder();
		configman = new ConfigManager();

		debug = false;
		onlineMap = new HashMap<String, Boolean>();

		/* BSTATS */////////////////////////
		@SuppressWarnings("unused")
		Metrics metrics = new Metrics(this);
		////////////////////////////////////

		// Create plugin folder if it doesn't exist
		if (!getDataFolder().exists()) {
			getLogger().info("Creating plugin directory!");
			getDataFolder().mkdirs();
		}

		// Start config loading
		configman.startupConfig();

		// If config isn't an allowed version
		if (! (Arrays.stream(ALLOWED_VERSIONS).anyMatch(x -> x.equals(configman.config.getString("version"))) ) ) {

			getLogger().warning("[SlingShot] CONFIG VERSION INCORRECT - Your config.yml is outdated. It will be saved as config-old.yml, and a fresh config.yml will be created.");

			File f = new File(SlingShot.configDir + File.separator + "config.yml");

			try {
				Files.copy(f.toPath(), new File(SlingShot.configDir, "config-old.yml").toPath(), new CopyOption[0]);
				Files.deleteIfExists(f.toPath());
				configman.startupConfig();
			} catch (IOException e) {
				getLogger().severe("[SlingShot] [ERROR] Could not move old config. Plugin will not work until this config is deleted.");
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

	/* MAIN PLUGIN BODY */

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.LOWEST)
	public void onServerKick(ServerKickEvent event) {

		/* Player is being kicked from a server... */

		debugMessage("--- START KICK EVENT ---");

		ProxiedPlayer p = event.getPlayer();
		ServerInfo s = event.getKickedFrom();

		debugMessage("Player: " + p.getName());
		debugMessage("Kicked From: " + s.getName());
		debugMessage("Kick Reason: " + event.getKickReason());

		// If event is already cancelled we don't need to do anything here...
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

		// If the player is kicked from a server that should not use SlingShot lets ignore it too
		if (configman.config.getStringList("no_slingshot").contains(s.getName())) {
			debugMessage("Player is kicked from a server that doesnt use slingshot");
			debugMessage("--- END KICK EVENT ---");
			return;
		}

		// If the kick reason is exempt from SlingShot
		if (isReasonExempt(event.getKickReason())) {
			debugMessage("Kick Reason is exempt from SlingShot!");
			debugMessage("--- END KICK EVENT ---");
			return;
		}

		/* END THE EXIT CONDITIONS */

		// Lets figure out what the best online target server is...
		Iterator<String> it = getTargetList().iterator();

		boolean found = false;
		String target = "";

		while (it.hasNext() && !found) {

			final String next = it.next();

			onlineMap.remove(next);

			getProxy().getServers().get(next).ping(new Callback<ServerPing>() {

				@Override
				public void done(ServerPing result, Throwable error) {

					boolean targetOnline = (error == null);

					synchronized (next) {

						// If the target server is online then connect to it, otherwise kick the player
						if (targetOnline) {
							onlineMap.put(next, true);
						} else {
							onlineMap.put(next, false);
						}
						next.notify();
					}

					return;

				}

			});

			// Wait for ping result
			synchronized (next) {
				try {
					next.wait(configman.config.getInt("timeout"));
				} catch (InterruptedException e) {
					System.err.println("SlingShot was interrupted while waiting for a response from your server. Your network must be lagging!s");
				}
			}

			// If it was a successful ping, we have found our server!
			if (onlineMap.containsKey(next)) {
				if (onlineMap.get(next)) {
					found = true;
					target = next;
				}
			}

		}

		// If none of the target servers responded, there is no where to send the player!
		if (!found) {

			debugMessage("No target servers are online.");
			debugMessage("Disconnect player with reason: " + configman.config.getString("kick-message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())));

			p.disconnect(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', configman.config.getString("kick-message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())))));

			debugMessage("--- END KICK EVENT ---");

			return;

		}

		/* If we get this far then we have identified the best target server */

		// If the player is being kicked from this server then we will just disconnect them!
		if (s.getName().equalsIgnoreCase(target)) {

			debugMessage("Player is being kicked from the target server");
			debugMessage("Disconnect with reason: " + configman.config.getString("kick-message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())));

			p.disconnect(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', configman.config.getString("kick-message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())))));

			debugMessage("--- END KICK EVENT ---");

			return;

		}

		/* If we get this far we need to send the player to the target server */

		debugMessage("This is a regular kick that needs dealing with by SlingShot.");

		debugMessage("The target server is online.");

		// Set the destination server for the cancelled kick
		event.setCancelServer(ProxyServer.getInstance().getServerInfo(target));

		debugMessage("Player will be connected to the target server on kick.");

		// Notify the player
		p.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', configman.config.getString("message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())))));

		debugMessage("Player notified with message: " + configman.config.getString("message").replace("%REASON%", BaseComponent.toLegacyText(event.getKickReasonComponent())));
		debugMessage("--- END KICK EVENT ---");

		event.setCancelled(true);

	}

}
