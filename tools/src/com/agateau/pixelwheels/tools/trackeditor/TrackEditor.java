/*
 * Copyright 2022 Aurélien Gâteau <mail@agateau.com>
 *
 * This file is part of Pixel Wheels.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.agateau.pixelwheels.tools.trackeditor;

import com.agateau.libgdx.AgcTmxMapLoader;
import com.agateau.pixelwheels.map.LapPosition;
import com.agateau.pixelwheels.map.LapPositionTable;
import com.agateau.pixelwheels.map.LapPositionTableIO;
import com.agateau.utils.log.NLog;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;

public class TrackEditor extends Game {
    private static class Args {
        String tmxFilePath;

        boolean parse(String[] arguments) {
            for (String arg : arguments) {
                if (arg.equals("-h") || arg.equals("--help")) {
                    showHelp();
                    return false;
                }
                if (arg.startsWith("-")) {
                    showError("Unknown option " + arg);
                    return false;
                }
                if (tmxFilePath == null) {
                    tmxFilePath = arg;
                } else {
                    showError("Too many arguments");
                    return false;
                }
            }
            if (tmxFilePath == null) {
                showError("Too few arguments");
                return false;
            }
            return true;
        }

        private static void showError(String message) {
            System.out.println("ERROR: " + message);
            showHelp();
        }

        private static void showHelp() {
            System.out.println("Usage: trackeditor [-h|--help] <tmxfile>");
        }
    }

    private final Args mArgs;

    public TrackEditor(Args args) {
        mArgs = args;
    }

    public static void main(String[] arguments) {
        Args args = new Args();
        if (!args.parse(arguments)) {
            System.exit(1);
        }

        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.width = 960;
        config.height = 540;
        config.title = "Track Editor";
        config.forceExit = false;
        new LwjglApplication(new TrackEditor(args), config);
    }

    @Override
    public void create() {
        setScreen(new TrackEditorScreen(Gdx.files.absolute(mArgs.tmxFilePath)));
    }

    public static void generateTable(FileHandle tmxFile, FileHandle tableFile) {
        TiledMap map = new AgcTmxMapLoader().load(tmxFile.path());
        LapPositionTable table = LapPositionTableIO.load(map);

        NLog.i("Drawing map");
        Pixmap pixmap = drawMap(map);
        NLog.i("Drawing table");
        drawTable(table, pixmap);
        NLog.i("Saving PNG");
        PixmapIO.writePNG(tableFile, pixmap);
    }

    private static Pixmap drawMap(TiledMap map) {
        TiledMapTileLayer layer = (TiledMapTileLayer) map.getLayers().get(0);
        int mapWidth = layer.getWidth() * layer.getTileWidth();
        int mapHeight = layer.getHeight() * layer.getTileHeight();

        FrameBuffer fbo =
                new FrameBuffer(Pixmap.Format.RGB888, mapWidth, mapHeight, false /* hasDepth */);
        OrthogonalTiledMapRenderer renderer = new OrthogonalTiledMapRenderer(map);

        OrthographicCamera camera = new OrthographicCamera();
        camera.setToOrtho(true /* yDown */, mapWidth, mapHeight);
        renderer.setView(camera);

        fbo.begin();
        renderer.render();

        return ScreenUtils.getFrameBufferPixmap(0, 0, mapWidth, mapHeight);
    }

    public static void drawTable(LapPositionTable table, Pixmap pixmap) {
        int width = pixmap.getWidth();
        int height = pixmap.getHeight();
        for (int y = 0; y < height; ++y) {
            int percent = 100 * y / (height - 1);
            System.out.print("\r" + percent + "%");
            for (int x = 0; x < width; ++x) {
                LapPosition pos = table.get(x, y);
                if (pos == null) {
                    continue;
                }
                int r = (int) ((1 - Math.abs(pos.getCenterDistance())) * 255);
                int g = pos.getSectionId() * 255 / table.getSectionCount();
                int b = (int) (pos.getSectionDistance() * 255);
                blendPixel(pixmap, x, height - 1 - y, r, g, b, 0.7f);
            }
        }
        System.out.println();
    }

    private static void blendPixel(Pixmap pixmap, int x, int y, int r, int g, int b, float k) {
        int mapColor = pixmap.getPixel(x, y);
        int mapR = (mapColor & 0xff000000) >>> 24;
        int mapG = (mapColor & 0xff0000) >>> 16;
        int mapB = (mapColor & 0xff00) >>> 8;

        int color = createPixelColor(lerpi(mapR, r, k), lerpi(mapG, g, k), lerpi(mapB, b, k));
        pixmap.drawPixel(x, y, color);
    }

    private static int createPixelColor(int r, int g, int b) {
        return (r << 24) | (g << 16) | (b << 8) | 0xff;
    }

    private static int lerpi(int from, int to, float progress) {
        return (int) MathUtils.lerp(from, to, progress);
    }
}
