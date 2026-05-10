package config

import "github.com/caarlos0/env/v11"

type Config struct {
	DatabaseURL string `env:"DATABASE_URL" envDefault:"postgres://url_shortener:url_shortener@localhost:5432/url_shortener?sslmode=disable"`
	BaseURL     string `env:"BASE_URL"     envDefault:"http://localhost:8080"`
	Port        int    `env:"PORT"         envDefault:"8080"`
	LogLevel    string `env:"LOG_LEVEL"    envDefault:"info"`
}

func Load() (*Config, error) {
	cfg := &Config{}
	if err := env.Parse(cfg); err != nil {
		return nil, err
	}
	return cfg, nil
}
