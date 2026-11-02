package io.aiplugin.core.local;

/**
 * 모델 다운로드 진행 상황.
 *
 * @param status    상태 메시지 (예: "downloading", "pulling manifest", "success")
 * @param completed 받은 바이트 수
 * @param total     전체 바이트 수 (모르면 0)
 */
public record DownloadProgress(String status, long completed, long total) {

    /** 0.0 ~ 1.0, 전체 크기를 모르면 -1. */
    public double fraction() {
        return total > 0 ? Math.min(1.0, (double) completed / total) : -1;
    }

    @Override
    public String toString() {
        return total > 0
                ? String.format("%s %.1f%% (%d / %d MB)", status, fraction() * 100, completed >> 20, total >> 20)
                : status;
    }
}
