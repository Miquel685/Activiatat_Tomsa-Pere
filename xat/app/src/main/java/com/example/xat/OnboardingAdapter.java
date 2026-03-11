package com.example.xat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.ViewHolder> {

    private String[] titles = {"TÍTOL 1", "TÍTOL 2", "TÍTOL 3"};
    private String[] descriptions = {
            "",
            "",
            ""
    };
    private int[] images = {R.drawable.logo_pere_son_gall_header_md, R.drawable.logo_pere_son_gall_header_md, R.drawable.logo_pere_son_gall_header_md}; // Recorda posar les fotos a drawable

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.title.setText(titles[position]);
        holder.desc.setText(descriptions[position]);
        holder.image.setImageResource(images[position]);
    }

    @Override
    public int getItemCount() {
        return titles.length;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc;
        ImageView image;

        public ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.txtTitle);
            desc = itemView.findViewById(R.id.txtDescription);
            image = itemView.findViewById(R.id.imgOnboarding);
        }
    }
}
