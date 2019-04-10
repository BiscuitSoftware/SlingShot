package xyz.olivermartin.slingshot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.CopyOption;
import java.nio.file.Files;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class ConfigManager {

	public Configuration config;

	public void startupConfig() {

		try {

			File file = new File(SlingShot.configDir, "config.yml");

			if (!file.exists()) {

				SlingShot.getInstance().getLogger().info("[SlingShot] Config.yml not found, creating!");
				saveDefaultConfig();
				loadConfig();

			} else {

				SlingShot.getInstance().getLogger().info("[SlingShot] Config.yml already exists, loading!");
				loadConfig();

			}

		} catch (Exception e) {

			e.printStackTrace();

		}
	}

	private void saveDefaultConfig() {

		try {

			InputStream in = getClass().getClassLoader().getResourceAsStream("config.yml");Throwable localThrowable3 = null;

			try {
				Files.copy(in, new File(SlingShot.configDir, "config.yml").toPath(), new CopyOption[0]);
			} catch (Throwable localThrowable1) {
				localThrowable3 = localThrowable1;throw localThrowable1;
			} finally {

				if (in != null) {
					if (localThrowable3 != null) {
						try {
							in.close();
						} catch (Throwable localThrowable2) {
							localThrowable3.addSuppressed(localThrowable2);
						}
					} else {
						in.close();
					}
				}

			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private void loadConfig() {

		try {
			this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(SlingShot.configDir, "config.yml"));
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
