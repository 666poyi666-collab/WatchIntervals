package com.poyi.watchintervals;

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;

/** AMap/高德 road tiles matching the map family embedded by OPPO Health. */
final class AmapTileSource extends OnlineTileSourceBase {
    AmapTileSource(){super("AMapSports",3,20,256,".png",new String[]{"https://webrd01.is.autonavi.com/appmaptile?","https://webrd02.is.autonavi.com/appmaptile?","https://webrd03.is.autonavi.com/appmaptile?","https://webrd04.is.autonavi.com/appmaptile?"},"高德地图");}
    @Override public String getTileURLString(long index){return getBaseUrl()+"style=7&x="+MapTileIndex.getX(index)+"&y="+MapTileIndex.getY(index)+"&z="+MapTileIndex.getZoom(index)+"&lang=zh_cn&size=1&scale=1";}
    static GeoPoint fromWgs84(double latitude,double longitude){if(outsideChina(latitude,longitude))return new GeoPoint(latitude,longitude);double dLat=transformLat(longitude-105d,latitude-35d),dLon=transformLon(longitude-105d,latitude-35d),radLat=latitude/180d*Math.PI;double magic=Math.sin(radLat);magic=1-0.00669342162296594323d*magic*magic;double sqrt=Math.sqrt(magic);dLat=(dLat*180d)/((6378245d*(1-0.00669342162296594323d))/(magic*sqrt)*Math.PI);dLon=(dLon*180d)/(6378245d/sqrt*Math.cos(radLat)*Math.PI);return new GeoPoint(latitude+dLat,longitude+dLon);}
    private static boolean outsideChina(double lat,double lon){return lon<72.004d||lon>137.8347d||lat<0.8293d||lat>55.8271d;}
    private static double transformLat(double x,double y){double r=-100+2*x+3*y+0.2*y*y+0.1*x*y+0.2*Math.sqrt(Math.abs(x));r+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;r+=(20*Math.sin(y*Math.PI)+40*Math.sin(y/3*Math.PI))*2/3;r+=(160*Math.sin(y/12*Math.PI)+320*Math.sin(y*Math.PI/30))*2/3;return r;}
    private static double transformLon(double x,double y){double r=300+x+2*y+0.1*x*x+0.1*x*y+0.1*Math.sqrt(Math.abs(x));r+=(20*Math.sin(6*x*Math.PI)+20*Math.sin(2*x*Math.PI))*2/3;r+=(20*Math.sin(x*Math.PI)+40*Math.sin(x/3*Math.PI))*2/3;r+=(150*Math.sin(x/12*Math.PI)+300*Math.sin(x/30*Math.PI))*2/3;return r;}
}
