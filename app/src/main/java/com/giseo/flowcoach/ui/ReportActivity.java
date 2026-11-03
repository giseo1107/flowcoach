package com.giseo.flowcoach.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.giseo.flowcoach.R;
import com.giseo.flowcoach.domain.CoachTuning;
import com.giseo.flowcoach.domain.TripReport;
import com.giseo.flowcoach.session.CoachRuntime;

import java.util.List;
import java.util.Locale;

/**
 * 주행 후 흐름 리포트. 정차 중에만 열리는 화면(Manifest에서 DO 미선언).
 *
 *  - 점수 링 + 등급 문장, 거리·시간
 *  - 흐름에 맞춘 시간 비율 막대(맞음·느림·초과, 정체는 평가 제외)
 *  - 다음 주행 한 가지: 가장 많이 깎인 항목의 팁
 *  - 점수에서 빠진 것: 항목별 횟수(시간)와 감점, 감점이 큰 것부터. 없으면 "✓ 없음"
 * 점수 계산은 TripReport.deductions()와 같은 식이라, 화면의 감점을 더하면 점수와 맞는다(반올림 차이 제외).
 *
 * 새 주행이 시작되면(D로 바꿔 자동 시작 등) 저절로 닫혀 코치 화면이 보이게 한다.
 * 리포트는 정차 중에 보는 화면이라 주행 중에 덮여 있으면 안 된다.
 */
public class ReportActivity extends AppCompatActivity {

    private static final String EXTRA_DISTANCE = "distance";
    private static final String EXTRA_DURATION = "duration";
    private static final String EXTRA_SMOOTH_SEC = "smoothSec";
    private static final String EXTRA_SLOW_SEC = "slowSec";
    private static final String EXTRA_OVER_SEC = "overSec";
    private static final String EXTRA_CONGESTED_SEC = "congestedSec";
    private static final String EXTRA_LANE_CHANGES = "laneChanges";
    private static final String EXTRA_PASSING = "passing";
    private static final String EXTRA_FREQUENT = "frequent";
    private static final String EXTRA_HARD_BRAKES = "hardBrakes";

    public static Intent intent(Context context, TripReport r) {
        return new Intent(context, ReportActivity.class)
                .putExtra(EXTRA_DISTANCE, r.getDistanceKm())
                .putExtra(EXTRA_DURATION, r.durationSec())
                .putExtra(EXTRA_SMOOTH_SEC, r.getSmoothSec())
                .putExtra(EXTRA_SLOW_SEC, r.getTooSlowSec())
                .putExtra(EXTRA_OVER_SEC, r.getOverLimitSec())
                .putExtra(EXTRA_CONGESTED_SEC, r.getCongestedSec())
                .putExtra(EXTRA_LANE_CHANGES, r.getLaneChanges())
                .putExtra(EXTRA_PASSING, r.getPassingHolds())
                .putExtra(EXTRA_FREQUENT, r.getFrequentLaneChanges())
                .putExtra(EXTRA_HARD_BRAKES, r.getHardBrakes());
    }

    private double smoothSec, slowSec, overSec, congestedSec;
    private int laneChanges, passing, frequent, hardBrakes;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** 1초마다: 새 주행이 시작됐으면 닫는다. */
    private final Runnable closeWhenDriving = new Runnable() {
        @Override
        public void run() {
            if (CoachRuntime.get(ReportActivity.this).isDriving()) {
                finish();
                return;
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        handler.postDelayed(closeWhenDriving, 1000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(closeWhenDriving);
        super.onPause();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);
        Intent in = getIntent();
        smoothSec = in.getDoubleExtra(EXTRA_SMOOTH_SEC, 0);
        slowSec = in.getDoubleExtra(EXTRA_SLOW_SEC, 0);
        overSec = in.getDoubleExtra(EXTRA_OVER_SEC, 0);
        congestedSec = in.getDoubleExtra(EXTRA_CONGESTED_SEC, 0);
        laneChanges = in.getIntExtra(EXTRA_LANE_CHANGES, 0);
        passing = in.getIntExtra(EXTRA_PASSING, 0);
        frequent = in.getIntExtra(EXTRA_FREQUENT, 0);
        hardBrakes = in.getIntExtra(EXTRA_HARD_BRAKES, 0);

        List<TripReport.Deduction> deductions =
                TripReport.deductions(smoothSec, slowSec, overSec, passing, frequent, hardBrakes);
        int score = TripReport.score(deductions);

        // 점수(처음 열 때만 차오르는 애니메이션)
        ScoreRingView ring = findViewById(R.id.score_ring);
        ring.setScore(score, TripReport.grade(score), savedInstanceState == null);

        long durationSec = in.getLongExtra(EXTRA_DURATION, 0);
        ((TextView) findViewById(R.id.summary_text)).setText(getString(R.string.report_summary,
                String.format(Locale.KOREA, "%.1f", in.getDoubleExtra(EXTRA_DISTANCE, 0)),
                CoachTuning.duration(durationSec * 1000)));

        renderShare();
        renderTip(deductions);
        renderRows(deductions);

        Button close = findViewById(R.id.close_button);
        close.setOnClickListener(v -> finish());
    }

    /** 흐름에 맞춘 시간 비율(평가한 시간 기준) + 색 설명. */
    private void renderShare() {
        double evaluated = smoothSec + slowSec + overSec;
        int smoothPct = pct(smoothSec, evaluated);
        int slowPct = pct(slowSec, evaluated);
        int overPct = evaluated > 0 ? Math.max(0, 100 - smoothPct - slowPct) : 0;
        ((TextView) findViewById(R.id.share_title)).setText(getString(R.string.report_share_title, smoothPct));
        ((TimeShareBarView) findViewById(R.id.share_bar)).setShares(smoothSec, slowSec, overSec, congestedSec);

        SpannableStringBuilder legend = new SpannableStringBuilder();
        legendItem(legend, R.color.state_smooth, getString(R.string.report_legend_smooth, smoothPct));
        if (slowSec > 0) legendItem(legend, R.color.state_slow, getString(R.string.report_legend_slow, slowPct));
        if (overSec > 0) legendItem(legend, R.color.state_over, getString(R.string.report_legend_over, overPct));
        if (congestedSec >= 1) {
            legendItem(legend, R.color.state_unknown, getString(R.string.report_legend_congested,
                    CoachTuning.duration(Math.round(congestedSec) * 1000)));
        }
        ((TextView) findViewById(R.id.share_legend)).setText(legend);
    }

    private void legendItem(SpannableStringBuilder sb, int colorRes, String text) {
        if (sb.length() > 0) sb.append("    ");
        int start = sb.length();
        sb.append("● ");
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, colorRes)), start, start + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(text);
    }

    /** 이보다 작은 감점(예: 가속 중 1초 느림 = 0.2점)은 팁으로 내세우지 않는다. */
    private static final double TIP_MIN_POINTS = 1.0;

    /** 가장 많이 깎인 항목의 팁. 큰 감점이 없으면 칭찬. */
    private void renderTip(List<TripReport.Deduction> deductions) {
        TripReport.Deduction top = deductions.get(0);
        TextView tip = findViewById(R.id.tip_text);
        if (top.points < TIP_MIN_POINTS) {
            tip.setText(R.string.report_tip_perfect);
            return;
        }
        tip.setText(getString(tipFor(top.item)) + " (" + getString(R.string.report_points, points(top.points)) + ")");
    }

    /** 점수에서 빠진 것: 감점 큰 것부터, 감점 없는 항목은 "✓ 없음". */
    private void renderRows(List<TripReport.Deduction> deductions) {
        LinearLayout box = findViewById(R.id.report_rows);
        LayoutInflater inflater = LayoutInflater.from(this);
        boolean first = true;
        for (TripReport.Deduction d : deductions) {
            View row = inflater.inflate(R.layout.item_report_row, box, false);
            row.findViewById(R.id.row_divider).setVisibility(first ? View.GONE : View.VISIBLE);
            first = false;
            ((TextView) row.findViewById(R.id.row_title)).setText(titleFor(d.item));
            ((TextView) row.findViewById(R.id.row_desc)).setText(descFor(d.item));
            TextView value = row.findViewById(R.id.row_value);
            TextView pts = row.findViewById(R.id.row_points);
            if (d.points <= 0) {
                value.setText(R.string.report_none);
                value.setTextColor(ContextCompat.getColor(this, R.color.state_smooth));
                pts.setVisibility(View.GONE);
            } else {
                value.setText(valueFor(d.item));
                pts.setText(getString(R.string.report_points, points(d.points)));
            }
            box.addView(row);
        }
    }

    private String valueFor(TripReport.Item item) {
        switch (item) {
            case PASSING: return getString(R.string.report_times, passing);
            case FREQUENT_LANE: return getString(R.string.report_warnings, frequent);
            case HARD_BRAKE: return getString(R.string.report_times, hardBrakes);
            case OVER_LIMIT: return CoachTuning.duration(Math.round(overSec) * 1000);
            case TOO_SLOW: return CoachTuning.duration(Math.round(slowSec) * 1000);
            default: return "";
        }
    }

    private int titleFor(TripReport.Item item) {
        switch (item) {
            case PASSING: return R.string.report_passing_title;
            case FREQUENT_LANE: return R.string.report_lane_title;
            case HARD_BRAKE: return R.string.report_brake_title;
            case OVER_LIMIT: return R.string.report_over_title;
            default: return R.string.report_slow_title;
        }
    }

    private String descFor(TripReport.Item item) {
        switch (item) {
            case PASSING: return getString(R.string.report_passing_desc);
            case FREQUENT_LANE: return getString(R.string.report_lane_desc, laneChanges);
            case HARD_BRAKE: return getString(R.string.report_brake_desc);
            case OVER_LIMIT: return getString(R.string.report_over_desc);
            default: return getString(R.string.report_slow_desc);
        }
    }

    private static int tipFor(TripReport.Item item) {
        switch (item) {
            case PASSING: return R.string.report_passing_tip;
            case FREQUENT_LANE: return R.string.report_lane_tip;
            case HARD_BRAKE: return R.string.report_brake_tip;
            case OVER_LIMIT: return R.string.report_over_tip;
            default: return R.string.report_slow_tip;
        }
    }

    /** 감점 표시: 1점 이상은 정수, 그 아래는 소수 한 자리. */
    private static String points(double p) {
        return p >= 1 ? String.valueOf(Math.round(p)) : String.format(Locale.KOREA, "%.1f", p);
    }

    private static int pct(double part, double whole) {
        return whole > 0 ? (int) Math.round(100 * part / whole) : 0;
    }
}
