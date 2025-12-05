package Evil.group.addon;

import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;

import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;

import Evil.group.addon.modules.WallHighwayGooner;
import Evil.group.addon.modules.Wither;

public class Evil_HWGooner extends MeteorAddon {
    public static final Category CATEGORY = new Category("Highway Gooner");

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public void onInitialize() {
        Modules.get().add(new WallHighwayGooner());
        Modules.get().add(new Wither());
    }
    @Override
    public String getPackage() {
        return "Evil.group.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("username", "Evil-Gooner");
    }
}
