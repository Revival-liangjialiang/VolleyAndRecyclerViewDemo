package com.example.k.lrucachedemo;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.annotation.Nullable;
import android.support.v4.util.LruCache;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    //列数等于3
    private int columsCount = 3;
    private RecyclerView recyclerView;
    //高速缓存的大小
    private int cacheSize;
    //跨度宽
    private int spanWidth;
    //缓存
    private LruCache<String, Bitmap> cache;
    private HashMap<String, ImageSize> sizeHashMap = new HashMap<String, ImageSize>();
    //初始页面大小
    private int initpageSize = 25;
    //刷新大小
    private int refreshSize = 10;
    //项计数
    private int itemCount = 0;
    //所有已加载的
    private boolean allLoaded;
    //没有更多的
    private boolean noMore = false;
    private Context context = this;
    private MyRecyclerAdapter adapter;
    private StaggeredGridLayoutManager layoutManager;
    //请求队列
    private RequestQueue requestqueue;
    //任务数
    private int taskCount = 0;
    //磁盘缓存大小，这里是50B*1024*1024,5M
    private int diskCacheSize = 50 * 1024 * 1024;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        //有固定的大小
        recyclerView.setHasFixedSize(true);
        //设置布局方式为StaggeredGridLayout，垂直排列列数为3
        layoutManager = new StaggeredGridLayoutManager(columsCount, StaggeredGridLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        //获取每列宽度
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        //得到默认的显示
        Display display = wm.getDefaultDisplay();
        //display.getWidth():得到屏幕的宽度像素值/3
        spanWidth = display.getWidth() / columsCount;
        //初始化Volley RequestQueue实例
        requestqueue = Volley.newRequestQueue(context);
        //设置内存缓存
        setLruCache();
        //初始化图片缓存
        refreshLruCache(initpageSize);
        adapter = new MyRecyclerAdapter(context, itemCount, requestqueue, spanWidth);
        recyclerView.setAdapter(adapter);
        adapter.setOnItemClickListener(new MyRecyclerAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View v, int position) {
                Toast.makeText(context, "you clicked " + position, Toast.LENGTH_SHORT).show();
            }
        });
        adapter.setOnItemLongClickListener(new MyRecyclerAdapter.OnItemLongClickListener() {
            @Override
            public void onItemLongClick(View v, int position) {
                Toast.makeText(MainActivity.this, "LongClick", Toast.LENGTH_SHORT).show();
            }
        });
        recyclerView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                //判断是否停止滚动
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    //判断当前加载是否完成
                    if (allLoaded) {
                        //得到每一列最后一个可见的元素的Position
                        int[] lastvisibalItem = layoutManager.findLastVisibleItemPositions(null);
                        int lastposition;
                        if (columsCount != 1) {
                            lastposition = Math.max(lastvisibalItem[0], lastvisibalItem[1]);
                            for (int i = 2; i < columsCount; i++) {
                                //获取整个视图可见元素中Position的最大值
                                lastposition = Math.max(lastposition, lastvisibalItem[i]);
                            }
                        } else {
                            lastposition = lastvisibalItem[0];
                        }
                        if ((lastposition + 1) == itemCount) {
                            //当最后一个可见元素的Position与加载的元素总数相等时，判断滑到底部，更新缓存、加载更多，
                            //为什么加11为不是10呢，这是因为lastposition要比实际项数少一，它是数组形式表示的数，从0开始，所以多加一，以消除误差
                            if ((lastposition + 11) <= ImageURLs.imageUrls.length) {
                                //当还剩余十个以上元素待加载时，加载10个元素
                                refreshLruCache(refreshSize);
                                Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show();
                            } else {
                                if (!noMore) {
                                    //当剩余元素不足十个时，加载剩余元素并提示
                                    int remaining = ImageURLs.imageUrls.length - lastposition - 1;
                                    refreshLruCache(remaining);
                                    Toast.makeText(context, "Loading...", Toast.LENGTH_SHORT).show();
                                    //没有更多图片
                                    noMore = true;
                                } else {
                                    //没有更多图片时提示
                                    Toast.makeText(context, "No more pictrues", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
            }
        });
    }

    private void refreshLruCache(final int refreshNum) {
        //加载状态设置为未完成
        allLoaded = false;
        //refreshNum:一次需要下载的数量
        if (refreshNum == 0) {
            allLoaded = true;
        }
        //itemCount：初始值为0
        for (int i = itemCount; i < itemCount + refreshNum; i++) {
            final int finalI = i;
            requestqueue.add(new ImageRequest(ImageURLs.imageUrls[i], new Response.Listener<Bitmap>() {
                @Override
                public void onResponse(Bitmap response) {
                    //将返回的Bitmap加入内存缓存
                    cache.put(ImageURLs.imageUrls[finalI], response);
                    ImageSize imageSize = new ImageSize(response.getWidth(), response.getHeight());
                    sizeHashMap.put(ImageURLs.imageUrls[finalI], imageSize);
                    //所有任务完成后将缓存传入Adapter并更新视图
                    taskCount++;
                    if (taskCount == refreshNum) {
                        adapter.setLruCache(cache);
                        adapter.setSizeHashMap(sizeHashMap);
                        //更新元素个数
                        itemCount = itemCount + refreshNum;
                        adapter.setItemCount(itemCount);
                        adapter.notifyDataSetChanged();
                        taskCount = 0;
                        //加载状态设置为全部完成
                        allLoaded = true;
                    }
                }
            }, spanWidth, 0, ImageView.ScaleType.CENTER_CROP, null, null));
        }

    }

    private void setLruCache() {
        //maxMemory()返回本进程的最大内存，以字节（B）为单位
        int maxCacheSize = (int) (Runtime.getRuntime().maxMemory() / 1024);
        //动用进程的六分之一内存
        cacheSize = maxCacheSize / 5;
        cache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                //getByteCount()：返回位图所占内存大小，以字节（B）为单位，除以1024等于?KB
                return value.getByteCount() / 1024;
            }
        };
    }
}
