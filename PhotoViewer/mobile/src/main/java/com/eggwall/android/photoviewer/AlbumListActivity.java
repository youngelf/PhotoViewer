package com.eggwall.android.photoviewer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * An Activity to show the list of Albums, and have the user pick out a single one.
 *
 * At some point, I would love to show some album attributes (number of images, last seen, whether
 * it is to be deleted soon, etc.) But for now, a LinearLayout that is both ugly and functional
 * at the same time would be sufficient.
 */
public class AlbumListActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_albumlist);

        // Wow, adding a Linear Layout after so many years. Since this is on the same process
        // as the MainActivity, I could even populate it by the database, but really there would
        // only be so many albums, so let's pass the full list of Albums as the starting intent
        // and read that.

        String[] array = {"test", "other", "third"};

        RecyclerView m = findViewById(R.id.album_list);
        m.setHasFixedSize(true);
        m.setLayoutManager(new LinearLayoutManager(this));

        MyAdapter adapter = new MyAdapter(array);
        m.setAdapter(adapter);

        m.setClickable(true);
    }

    public static class MyAdapter extends RecyclerView.Adapter<MyAdapter.MyViewHolder> {
        private String[] mDataset;

        static class MyViewHolder extends RecyclerView.ViewHolder {
            TextView mTV;
            // The object this refers to.
            String albumId;

            MyViewHolder(TextView v, String albumId) {
                super(v);
                mTV = v;
                this.albumId = albumId;
            }
        }

        MyAdapter(String[] dataset) {
            mDataset = dataset;
        }

        @Override
        public void registerAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
            super.registerAdapterDataObserver(observer);
        }

        @NonNull
        @Override
        public MyViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            // We are giving out TextView objects, change these to more appealing View objects.
            TextView v = (TextView) LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.list_item, viewGroup, false);
            return new MyViewHolder(v, mDataset[i]);
        }

        @Override
        public void onBindViewHolder(@NonNull MyViewHolder myViewHolder, int i) {
            myViewHolder.mTV.setText(mDataset[i]);

        }

        @Override
        public int getItemCount() {
            return mDataset.length;
        }
    }
}
