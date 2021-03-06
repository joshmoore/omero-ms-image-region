/*
 * Copyright (C) 2017 Glencoe Software, Inc. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.glencoesoftware.omero.ms.image.region;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;

import com.glencoesoftware.omero.ms.core.OmeroRequestCtx;

import io.vertx.core.MultiMap;
import omeis.providers.re.data.RegionDef;;

public class ImageRegionCtx extends OmeroRequestCtx {

    private static final org.slf4j.Logger log =
            LoggerFactory.getLogger(ImageRegionCtx.class);

    /** Image Id*/
    public Long imageId;

    /** z - index */
    public Integer z;

    /** t - index */
    public Integer t;

    /**
     * Region descriptor (tile); only X and Y are used at this stage and
     * represent the <b>tile</b> offset rather than the pixel offset
     */
    public RegionDef tile;

    /** Resolution to read */
    public Integer resolution;

    /**
     * Region descriptor (region); X, Y, width, and height are used at this
     * stage and represent the pixel offset in all cases
     */
    public RegionDef region;

    /** Channel settings - handled at the Verticle level*/
    public List<Integer> channels;
    public List<Float[]> windows;
    public List<String> colors;

    /** Color mode (g == grey scale; c == rgb) */
    public String m;

    /** Maps. <b>Not</b> handled at the moment. Supported from 5.3.0 */
    public String maps;

    /** Compression quality */
    public Float compressionQuality;

    /**
     * Projection 'intmax' OR 'intmax|5:25'
     * NOT handled at the moment - does not look like it's supported
     * for renderImageRegion: https://github.com/openmicroscopy/openmicroscopy/blob/be40a59300bb73a22b72eac00dd24b2aa54e4768/components/tools/OmeroPy/src/omero/gateway/__init__.py#L8758
     * vs. renderImage: https://github.com/openmicroscopy/openmicroscopy/blob/be40a59300bb73a22b72eac00dd24b2aa54e4768/components/tools/OmeroPy/src/omero/gateway/__init__.py#L8837
     * */
    public String projection;

    /**
     * Inverted Axis
     * NOT handled at the moment - no use cases
     * */
    public Boolean invertedAxis;

    /**
     * Constructor for jackson to decode the object from string
     */
    ImageRegionCtx() {};

    /**
     * Default constructor.
     * @param params {@link io.vertx.core.http.HttpServerRequest} parameters
     * required for rendering an image region.
     * @param omeroSessionKey OMERO session key.
     */
    ImageRegionCtx(MultiMap params, String omeroSessionKey) {
        this.omeroSessionKey = omeroSessionKey;
        imageId = Long.parseLong(params.get("imageId"));
        z = Integer.parseInt(params.get("z"));
        t = Integer.parseInt(params.get("t"));
        getTileFromString(params.get("tile"));
        getRegionFromString(params.get("region"));
        getChannelInfoFromString(params.get("c"));
        getColorModelFromString(params.get("m"));
        getCompressionQualityFromString(params.get("q"));
        getInvertedAxisFromString(params.get("ia"));
        projection = params.get("p");
        maps = params.get("maps");

        log.debug("{}, z: {}, t: {}, tile: {}, c: [{}, {}, {}], m: {}",
                imageId, z, t, tile, channels, windows, colors, m);
    }

    /**
     * Parse a string to RegionDef and Int describing tile and resolution.
     * @param tileString string describing the tile to render:
     * "1,1,0,1024,1024"
     */
    private void getTileFromString(String tileString) {
        if (tileString == null) {
            return;
        }
        String[] tileArray = tileString.split(",", -1);
        tile = new RegionDef();
        tile.setX(Integer.parseInt(tileArray[1]));
        tile.setY(Integer.parseInt(tileArray[2]));
        resolution = Integer.parseInt(tileArray[0]);
    }

    /**
     * Parse a string to RegionDef.
     * @param regionString string describing the region to render:
     * "0,0,1024,1024"
     */
    private void getRegionFromString(String regionString) {
        if (regionString == null) {
            return;
        }
        String[] regionSplit = regionString.split(",", -1);
        region = new RegionDef(
            Integer.parseInt(regionSplit[0]),
            Integer.parseInt(regionSplit[1]),
            Integer.parseInt(regionSplit[2]),
            Integer.parseInt(regionSplit[3])
        );
    }

    /**
     * Parses a string to channel rendering settings.
     * Populates channels, windows and colors lists.
     * @param channelInfo string describing the channel rendering settings:
     * "-1|0:65535$0000FF,2|1755:51199$00FF00,3|3218:26623$FF0000"
     */
    private void getChannelInfoFromString(String channelInfo) {
        if (channelInfo == null) {
            return;
        }
        String[] channelArray = channelInfo.split(",", -1);
        channels = new ArrayList<Integer>();
        windows = new ArrayList<Float[]>();
        colors = new ArrayList<String>();
        for (String channel : channelArray) {
            // chan  1|12:1386r$0000FF
            // temp ['1', '12:1386r$0000FF']
            String[] temp = channel.split("\\|", 2);
            String active = temp[0];
            String color = null;
            Float[] range = new Float[2];
            String window = null;
            // temp = '1'
            // Not normally used...
            if (active.indexOf("$") >= 0) {
                String[] split = active.split("\\$", -1);
                active = split[0];
                color = split[1];
            }
            channels.add(Integer.parseInt(active));
            if (temp.length > 1) {
                if (temp[1].indexOf("$") >= 0) {
                    window = temp[1].split("\\$")[0];
                    color = temp[1].split("\\$")[1];
                }
                String[] rangeStr = window.split(":");
                if (rangeStr.length > 1) {
                    range[0] = Float.parseFloat(rangeStr[0]);
                    range[1] = Float.parseFloat(rangeStr[1]);
                }
            }
            colors.add(color);
            windows.add(range);
            log.debug("Adding channel: {}, color: {}, window: {}",
                    active, color, window);
        }
    }

    /**
     * Parses color model input to the string accepted by the rendering engine.
     * @param colorModel string describing color model:
     * "g" for greyscale and "c" for rgb.
     */
    private void getColorModelFromString(String colorModel) {
        if ("g".equals(colorModel)) {
            m = "greyscale";
        } else if ("c".equals(colorModel)) {
            m = "rgb";
        } else {
            m = null;
        }
    }

    /**
     * Parses string to Float and sets it as compressionQuality.
     * @param quality accepted values: [0, 1]
     */
    private void getCompressionQualityFromString(String quality) {
        compressionQuality = quality == null? null : Float.parseFloat(quality);
    }

    /**
     * Parses string to boolean and sets it as inverted axis.
     * @param iaString accepted values: 0 - False, 1 - True
     */
    private void getInvertedAxisFromString(String iaString) {
        invertedAxis = iaString == null? null : Boolean.parseBoolean(iaString);
    }
}
