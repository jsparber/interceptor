package com.juliansparber.vpnMITM;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;

import java.util.Calendar;

import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        View aboutPage = new AboutPage(this)
                .isRTL(false)
                .setImage(R.drawable.ic_launcher)
                .setDescription((String) getText(R.string.about_page_description))
                .addItem(new Element().setTitle("Version 1.0"))
                .addEmail("julian@sparber.net", getString(R.string.about_contact_us))
                .addWebsite("http://jsparber.github.io/", getString(R.string.about_website))
                .addGitHub("jsparber/vpnMITM", getString(R.string.about_github))
                .addGroup("Licence:")
                .addItem(getLicence())
                .addGroup("External Code and Libraries:")
                .addItem(externalLibs(0))
                .addItem(externalLibs(1))
                .addItem(externalLibs(2))
                .addItem(externalLibs(3))
                .addItem(getCopyRightsElement())
                .create();

        setContentView(aboutPage);
    }

    Element getCopyRightsElement() {
        Element copyRightsElement = new Element();
        final String copyrights = String.format(getString(R.string.copy_right), Calendar.getInstance().get(Calendar.YEAR));
        copyRightsElement.setTitle(copyrights);
        //copyRightsElement.setIconDrawable(R.drawable.about_icon_copy_right);
        copyRightsElement.setIconTint(mehdi.sakout.aboutpage.R.color.about_item_icon_color);
        copyRightsElement.setIconNightTint(android.R.color.white);
        copyRightsElement.setGravity(Gravity.CENTER);
        return copyRightsElement;
    }
    Element getLicence() {
        Element licence = new Element();
        String gplv3 = "This program is free software: you can redistribute it and/or modify " +
                "it under the terms of the GNU General Public License as published by " +
                "the Free Software Foundation, either version 3 of the License, or " +
                "(at your option) any later version.\n" +
                "\n" +
                "This program is distributed in the hope that it will be useful, " +
                "but WITHOUT ANY WARRANTY; without even the implied warranty of " +
                "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the " +
                "GNU General Public License for more details.\n" +
                "\n" +
                "You should have received a copy of the GNU General Public License " +
                "along with this program.\nIf not, see <http://www.gnu.org/licenses/>";
        licence.setTitle(gplv3);
        Uri uri = Uri.parse("https://www.gnu.org/licenses/");
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
        licence.setIntent(browserIntent);

        return  licence;
    }

    Element externalLibs(int id) {
        Element el = new Element();
        String[] libs = new String[4];
        String[] url = new String[4];
        libs[0] = "LocalVPN by Mohamed Naufal\n(Apache License, Version 2.0)";
        url[0] = "https://github.com/hexene/LocalVPN";
        libs[1] = "Net Monitor by Felix Tsala Schille\n(GNU General Public License, Version 3)";
        url[1] = "https://github.com/SecUSo/privacy-friendly-netmonitor";
        libs[2] = "NetGuard by Marcel Bokhorst\n(GNU General Public License, Version 3)";
        url[2] = "https://github.com/M66B/NetGuard";
        libs[3] = "android-about-page by Mehdi Sakout\n(The MIT License)";
        url[3] = "https://github.com/medyo/android-about-page";
        if (id >=0 && id < 4) {
            el.setTitle(libs[id]);
            Uri uri = Uri.parse(url[id]);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            el.setIntent(browserIntent);
        }
        return el;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

}
